package com.urbanstay.earlycheckin.rule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomView;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Candidate;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Excluded;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Exclusion;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Reason;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.RequestMatch;

/**
 * 객실 배정 추천. 후보 거르기(타입, 기간 겹침, 운영 중지) → 요청사항 비교 → 정렬(요청 일치 수, 전날 공실, 객실 번호) 순으로
 * 판단한다. 최적화가 아니라 충돌 없는 후보와 추천 이유를 보여주는 데 목적이 있다.
 */
public final class RoomAssignmentRule {

	/** 숫자 호수를 수 크기대로 정렬한다 (802 → 1203). */
	private static final Comparator<String> ROOM_NUMBER_ORDER = Comparator.comparingInt(String::length)
		.thenComparing(Comparator.naturalOrder());

	private final CheckInPolicy policy;

	public RoomAssignmentRule(CheckInPolicy policy) {
		this.policy = policy;
	}

	/** 배정을 바꿀 수 없는 이유 */
	public enum Lock {
		/** 이미 입실(또는 퇴실)한 예약 */
		CHECKED_IN,
		/** 정오 안내나 얼리체크인 제안이 이미 나간 예약 (제안은 특정 객실이 준비되어 나간 것이라 객실을 바꿀 수 없다) */
		NOTICE_SENT
	}

	public Optional<Lock> lock(ReservationSnapshot target) {
		if (target.status() == ReservationStatus.CHECKED_IN || target.status() == ReservationStatus.CHECKED_OUT) {
			return Optional.of(Lock.CHECKED_IN);
		}
		if (target.noonNoticeSentAt() != null || target.offerStatus() != null) {
			return Optional.of(Lock.NOTICE_SENT);
		}
		return Optional.empty();
	}

	/**
	 * @param reservations 대상 예약의 숙박 기간 근처 예약 (기간 겹침과 전날 숙박 판단용). 대상 예약이 들어 있어도 된다.
	 */
	public AssignmentOptions options(ReservationSnapshot target, Collection<RoomSnapshot> rooms,
			Collection<ReservationSnapshot> reservations) {
		List<ReservationSnapshot> others = reservations.stream().filter(r -> r.id() != target.id()).toList();
		List<Candidate> candidates = new ArrayList<>();
		List<Excluded> excluded = new ArrayList<>();
		for (RoomSnapshot room : rooms) {
			List<Exclusion> reasons = exclusions(target, room, others);
			if (reasons.isEmpty()) {
				candidates.add(new Candidate(room.number(), matches(target, room),
						vacantNightBefore(room, target.checkInDate(), others)));
			}
			else {
				excluded.add(new Excluded(room.number(), reasons));
			}
		}
		candidates.sort(Comparator.comparingLong(Candidate::matchCount)
			.reversed()
			.thenComparing(Candidate::vacantLastNight, Comparator.reverseOrder())
			.thenComparing(Candidate::roomNumber, ROOM_NUMBER_ORDER));
		excluded.sort(Comparator.comparing((Excluded e) -> e.reasons()
			.stream()
			.anyMatch(reason -> reason.reason() == Reason.ROOM_TYPE_MISMATCH))
			.thenComparing(Excluded::roomNumber, ROOM_NUMBER_ORDER));
		return new AssignmentOptions(candidates, excluded);
	}

	private List<Exclusion> exclusions(ReservationSnapshot target, RoomSnapshot room,
			List<ReservationSnapshot> others) {
		List<Exclusion> reasons = new ArrayList<>();
		if (room.type() != target.roomType()) {
			reasons.add(new Exclusion(Reason.ROOM_TYPE_MISMATCH, null));
		}
		if (room.status() == RoomStatus.OUT_OF_SERVICE) {
			reasons.add(new Exclusion(Reason.OUT_OF_SERVICE, null));
		}
		others.stream()
			.filter(r -> Objects.equals(r.roomNumber(), room.number()))
			.filter(r -> overlaps(r, target))
			.forEach(r -> reasons.add(new Exclusion(Reason.STAY_OVERLAP, r.id())));
		return reasons;
	}

	/** 숙박 기간은 [체크인, 체크아웃) 구간이라 체크아웃 당일의 다음 체크인은 겹치지 않는다. */
	private static boolean overlaps(ReservationSnapshot a, ReservationSnapshot b) {
		return a.checkInDate().isBefore(b.checkOutDate()) && b.checkInDate().isBefore(a.checkOutDate());
	}

	private List<RequestMatch> matches(ReservationSnapshot target, RoomSnapshot room) {
		return target.requests().stream().map(request -> new RequestMatch(request, satisfies(room, request))).toList();
	}

	private boolean satisfies(RoomSnapshot room, RoomRequest request) {
		return switch (request) {
			case HIGH_FLOOR -> room.floor() >= policy.highFloorFrom();
			case CITY_VIEW -> room.view() == RoomView.CITY;
		};
	}

	/** 체크인 전날 밤에 이 방에 묵은 예약이 없으면 당일 퇴실 청소가 없어 일찍 준비된다. */
	private static boolean vacantNightBefore(RoomSnapshot room, LocalDate checkInDate,
			List<ReservationSnapshot> others) {
		return others.stream()
			.filter(r -> Objects.equals(r.roomNumber(), room.number()))
			.noneMatch(r -> r.checkInDate().isBefore(checkInDate) && !r.checkOutDate().isBefore(checkInDate));
	}

}
