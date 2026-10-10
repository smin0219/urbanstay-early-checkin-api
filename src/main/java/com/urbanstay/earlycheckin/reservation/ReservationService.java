package com.urbanstay.earlycheckin.reservation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.Room;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.evaluation.RuleContexts;
import com.urbanstay.earlycheckin.evaluation.RuleEvaluationService;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.Assignment;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.CandidateItem;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.ConflictingStay;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.Detail;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.ExcludedItem;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.ExclusionItem;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.RoomInfo;
import com.urbanstay.earlycheckin.rule.AssignmentOptions;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.OperatorAlert;
import com.urbanstay.earlycheckin.rule.ReservationSnapshot;
import com.urbanstay.earlycheckin.rule.RoomAssignmentRule;
import com.urbanstay.earlycheckin.rule.RuleContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

	private final Clock clock;

	private final CheckInRuleEngine engine;

	private final RoomAssignmentRule assignmentRule;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final RuleEvaluationService evaluationService;

	public ReservationDetailResponse detail(long id) {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation target = find(id);
		Map<String, Room> rooms = roomsByNumber();
		RuleContext ctx = context(now, rooms.values(), target);
		ReservationSnapshot snapshot = RuleContexts.snapshot(target);
		List<OperatorAlert> alerts = engine.alerts(ctx).stream().filter(alert -> alert.reservationId() == id).toList();
		return new ReservationDetailResponse(now, toDetail(ctx, target, snapshot), alerts, assignment(target, rooms));
	}

	/** 추천 후보 중 하나로 배정한다. 정오 이후라면 평가에서 정오 안내가 바로 나간다. */
	@Transactional
	public ReservationDetailResponse assign(long id, String roomNumber) {
		Reservation target = find(id);
		requireUnlocked(target);
		Map<String, Room> rooms = roomsByNumber();
		boolean candidate = options(target, rooms.values(), around(target)).candidates()
			.stream()
			.anyMatch(c -> c.roomNumber().equals(roomNumber));
		if (!candidate) {
			throw new IllegalStateException(roomNumber + "호는 이 예약에 배정할 수 없는 객실입니다.");
		}
		target.assign(rooms.get(roomNumber), LocalDateTime.now(clock));
		evaluationService.evaluate();
		return detail(id);
	}

	/** 배정을 취소해 배정 대기로 되돌린다. 배정을 바꿀 수 있는 동안(정오 안내 전)만 가능하다. */
	@Transactional
	public ReservationDetailResponse unassign(long id) {
		Reservation target = find(id);
		requireUnlocked(target);
		if (target.getRoom() == null) {
			throw new IllegalStateException("배정된 객실이 없습니다.");
		}
		target.unassign();
		evaluationService.evaluate();
		return detail(id);
	}

	private void requireUnlocked(Reservation target) {
		assignmentRule.lock(RuleContexts.snapshot(target)).ifPresent(lock -> {
			throw new IllegalStateException(lockMessage(lock));
		});
	}

	private Assignment assignment(Reservation target, Map<String, Room> rooms) {
		RoomAssignmentRule.Lock lock = assignmentRule.lock(RuleContexts.snapshot(target)).orElse(null);
		if (lock != null) {
			return new Assignment(lock, List.of(), List.of());
		}
		Map<Long, Reservation> around = around(target).stream()
			.collect(Collectors.toMap(Reservation::getId, Function.identity()));
		AssignmentOptions options = options(target, rooms.values(), around.values());
		String current = target.getRoom() == null ? null : target.getRoom().getNumber();

		List<CandidateItem> candidates = options.candidates()
			.stream()
			.map(c -> new CandidateItem(roomInfo(rooms.get(c.roomNumber())), c.matches(), c.matchCount(),
					c.vacantLastNight(), c.roomNumber().equals(current)))
			.toList();
		List<ExcludedItem> excluded = options.excluded()
			.stream()
			.map(e -> new ExcludedItem(roomInfo(rooms.get(e.roomNumber())), e.reasons()
				.stream()
				.map(reason -> new ExclusionItem(reason.reason(),
						reason.reservationId() == null ? null : conflict(around.get(reason.reservationId()))))
				.toList()))
			.toList();
		return new Assignment(null, candidates, excluded);
	}

	private AssignmentOptions options(Reservation target, Collection<Room> rooms, Collection<Reservation> around) {
		return assignmentRule.options(RuleContexts.snapshot(target),
				rooms.stream().map(RuleContexts::snapshot).toList(),
				around.stream().map(RuleContexts::snapshot).toList());
	}

	private List<Reservation> around(Reservation target) {
		return reservationRepository.findAround(target.getCheckInDate(), target.getCheckOutDate());
	}

	/** 다음 할 일·알림 판단용 컨텍스트. 오늘 묵는 예약에 대상 예약을 더한다 (미래·과거 예약도 볼 수 있게). */
	private RuleContext context(LocalDateTime now, Collection<Room> rooms, Reservation target) {
		List<Reservation> reservations = new ArrayList<>(reservationRepository.findStayingOn(now.toLocalDate()));
		if (reservations.stream().noneMatch(r -> r.getId().equals(target.getId()))) {
			reservations.add(target);
		}
		return RuleContexts.of(now, rooms, reservations);
	}

	private Detail toDetail(RuleContext ctx, Reservation r, ReservationSnapshot snapshot) {
		return new Detail(r.getId(), r.getGuestName(), r.getRoomType(), r.getCheckInDate(), r.getCheckOutDate(),
				r.getCheckOutTime(), (int) ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate()),
				snapshot.requests(), r.getStatus(), r.getRoom() == null ? null : roomInfo(r.getRoom()),
				r.getAssignedAt(), engine.expectedCheckIn(snapshot), r.getOfferStatus(), r.getEarlyCheckInAt(),
				r.getEarlyCheckInFee(), engine.nextStep(ctx, snapshot).orElse(null), r.getAccessToken());
	}

	private static RoomInfo roomInfo(Room room) {
		return new RoomInfo(room.getNumber(), room.getType(), room.getFloor(), room.getView(), room.getStatus(),
				room.getReadyAt());
	}

	private static ConflictingStay conflict(Reservation r) {
		return new ConflictingStay(r.getId(), r.getGuestName(), r.getCheckInDate(), r.getCheckOutDate());
	}

	private static String lockMessage(RoomAssignmentRule.Lock lock) {
		return switch (lock) {
			case CHECKED_IN -> "이미 입실한 예약은 배정을 바꿀 수 없습니다.";
			case NOTICE_SENT -> "정오 안내나 얼리체크인 제안이 나간 예약은 배정을 바꾸거나 취소할 수 없습니다.";
		};
	}

	private Reservation find(long id) {
		return reservationRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("예약 " + id + "을(를) 찾을 수 없습니다."));
	}

	private Map<String, Room> roomsByNumber() {
		return roomRepository.findAll().stream().collect(Collectors.toMap(Room::getNumber, Function.identity()));
	}

}
