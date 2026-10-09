package com.urbanstay.earlycheckin.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.Room;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomStatusChangeRepository;
import com.urbanstay.earlycheckin.evaluation.RuleContexts;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.Action;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.Arrival;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.MessageEntry;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.RoomInfo;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.Stay;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.TimelineEntry;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.ReservationSnapshot;
import com.urbanstay.earlycheckin.rule.RuleContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

	private final Clock clock;

	private final CheckInRuleEngine engine;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final RoomStatusChangeRepository roomStatusChangeRepository;

	private final MessageLogRepository messageLogRepository;

	public RoomDetailResponse detail(String number) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		Room room = roomRepository.findById(number)
			.orElseThrow(() -> new NoSuchElementException(number + "호를 찾을 수 없습니다."));
		List<Reservation> staying = reservationRepository.findStayingOn(today);
		RuleContext ctx = RuleContexts.of(now, roomRepository.findAll(), staying);
		List<Reservation> inRoom = staying.stream()
			.filter(r -> r.getRoom() != null && r.getRoom().getNumber().equals(number))
			.toList();

		Optional<Reservation> arrival = inRoom.stream()
			.filter(r -> r.getCheckInDate().equals(today) && r.getStatus() != ReservationStatus.CHECKED_OUT)
			.findFirst();
		Optional<Reservation> departure = inRoom.stream()
			.filter(r -> r.getCheckOutDate().equals(today))
			.filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN || r.getStatus() == ReservationStatus.CHECKED_OUT)
			.findFirst();
		Optional<Reservation> inHouse = inRoom.stream()
			.filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN && r.getCheckOutDate().isAfter(today))
			.filter(r -> arrival.map(a -> !a.getId().equals(r.getId())).orElse(true))
			.findFirst();

		return new RoomDetailResponse(now, roomInfo(room), inHouse.map(RoomService::stay).orElse(null),
				departure.map(RoomService::stay).orElse(null), arrival.map(r -> arrival(ctx, r)).orElse(null),
				actions(room, departure), engine.explain(ctx, number),
				engine.alerts(ctx).stream().filter(alert -> number.equals(alert.roomNumber())).toList(),
				roomStatusChangeRepository.findByRoomNumberOrderByChangedAtAscIdAsc(number)
					.stream()
					.map(c -> new TimelineEntry(c.getChangedAt(), c.getEvent(), c.getFromStatus(), c.getToStatus(),
							c.getGuestName()))
					.toList(),
				messageLogRepository.findByRoomNumberOrderBySentAtAscIdAsc(number)
					.stream()
					.map(m -> new MessageEntry(m.getSentAt(), m.getType(), m.getReservation().getId(),
							m.getReservation().getGuestName(), m.getRoomStatus(), m.getContent()))
					.toList());
	}

	private static List<Action> actions(Room room, Optional<Reservation> departure) {
		List<Action> actions = new ArrayList<>();
		boolean departing = departure.map(r -> r.getStatus() == ReservationStatus.CHECKED_IN).orElse(false);
		if (room.getStatus() == RoomStatus.OCCUPIED && departing) {
			actions.add(Action.CHECK_OUT);
		}
		if (room.getStatus() == RoomStatus.CLEANING_PENDING) {
			actions.add(Action.START_CLEANING);
		}
		if (room.getStatus() == RoomStatus.CLEANING) {
			actions.add(Action.FINISH_CLEANING);
		}
		return actions;
	}

	private Arrival arrival(RuleContext ctx, Reservation r) {
		ReservationSnapshot snapshot = RuleContexts.snapshot(r);
		return new Arrival(r.getId(), r.getGuestName(), r.getStatus(), engine.expectedCheckIn(snapshot),
				r.getOfferStatus(), r.getEarlyCheckInAt(), r.getEarlyCheckInFee(), r.getCheckedInAt(),
				engine.nextStep(ctx, snapshot).orElse(null));
	}

	private static Stay stay(Reservation r) {
		return new Stay(r.getId(), r.getGuestName(), r.getCheckInDate(), r.getCheckOutDate(),
				r.getCheckOutDate().atTime(r.getCheckOutTime()), r.getStatus(), r.getCheckedOutAt());
	}

	private static RoomInfo roomInfo(Room room) {
		return new RoomInfo(room.getNumber(), room.getType(), room.getFloor(), room.getView(), room.getStatus(),
				room.getReadyAt());
	}

}
