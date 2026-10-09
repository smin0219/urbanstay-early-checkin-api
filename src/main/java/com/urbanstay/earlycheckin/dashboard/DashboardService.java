package com.urbanstay.earlycheckin.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.dashboard.DashboardResponse.AlertItem;
import com.urbanstay.earlycheckin.dashboard.DashboardResponse.ArrivalRow;
import com.urbanstay.earlycheckin.dashboard.DashboardResponse.Summary;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.evaluation.RuleContexts;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.OperatorAlert;
import com.urbanstay.earlycheckin.rule.ReservationSnapshot;
import com.urbanstay.earlycheckin.rule.RuleContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

	private final Clock clock;

	private final CheckInRuleEngine engine;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final MessageLogRepository messageLogRepository;

	public DashboardResponse dashboard() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		List<Reservation> staying = reservationRepository.findStayingOn(today);
		RuleContext ctx = RuleContexts.of(now, roomRepository.findAll(), staying);

		List<Reservation> arrivals = staying.stream()
			.filter(r -> r.getCheckInDate().equals(today))
			.sorted(Comparator.comparing(Reservation::getId))
			.toList();
		Map<Long, Reservation> arrivalsById = arrivals.stream()
			.collect(Collectors.toMap(Reservation::getId, Function.identity()));

		List<AlertItem> alerts = engine.alerts(ctx).stream()
			.map(alert -> toAlertItem(alert, arrivalsById.get(alert.reservationId())))
			.toList();
		List<ArrivalRow> rows = arrivals.stream().map(r -> toRow(ctx, r)).toList();
		return new DashboardResponse(now, summarize(arrivals), alerts, rows);
	}

	private Summary summarize(List<Reservation> arrivals) {
		List<Reservation> waiting = arrivals.stream()
			.filter(r -> r.getStatus() != ReservationStatus.CHECKED_IN)
			.toList();
		List<Reservation> accepted = arrivals.stream()
			.filter(r -> r.getOfferStatus() == OfferStatus.ACCEPTED)
			.toList();
		return new Summary(
				arrivals.size(),
				count(arrivals, r -> r.getStatus() == ReservationStatus.CHECKED_IN),
				count(waiting, r -> r.getRoom() == null),
				count(waiting, roomIn(RoomStatus.OCCUPIED)),
				count(waiting, roomIn(RoomStatus.CLEANING_PENDING).or(roomIn(RoomStatus.CLEANING))),
				count(waiting, roomIn(RoomStatus.READY)),
				count(arrivals, r -> r.getOfferStatus() != null),
				accepted.size(),
				accepted.stream().mapToLong(r -> r.getEarlyCheckInFee() == null ? 0 : r.getEarlyCheckInFee()).sum(),
				messageLogRepository.countByTypeAndRoomStatusNot(MessageType.PASSWORD, RoomStatus.READY));
	}

	private AlertItem toAlertItem(OperatorAlert alert, Reservation arrival) {
		return new AlertItem(alert.type(), alert.level(), alert.since(), alert.roomNumber(), alert.reservationId(),
				arrival.getGuestName(), engine.expectedCheckIn(RuleContexts.snapshot(arrival)));
	}

	private ArrivalRow toRow(RuleContext ctx, Reservation r) {
		ReservationSnapshot snapshot = RuleContexts.snapshot(r);
		return new ArrivalRow(
				r.getId(),
				r.getGuestName(),
				r.getRoomType(),
				r.getRequests().stream().sorted().toList(),
				r.getRoom() == null ? null : r.getRoom().getNumber(),
				r.getRoom() == null ? null : r.getRoom().getStatus(),
				engine.expectedCheckIn(snapshot),
				r.getStatus(),
				r.getOfferStatus(),
				r.getEarlyCheckInAt(),
				r.getEarlyCheckInFee(),
				engine.nextStep(ctx, snapshot).orElse(null));
	}

	private static Predicate<Reservation> roomIn(RoomStatus status) {
		return r -> r.getRoom() != null && r.getRoom().getStatus() == status;
	}

	private static int count(List<Reservation> reservations, Predicate<Reservation> predicate) {
		return (int) reservations.stream().filter(predicate).count();
	}

}
