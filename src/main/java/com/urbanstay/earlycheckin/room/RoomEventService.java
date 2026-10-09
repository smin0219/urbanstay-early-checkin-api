package com.urbanstay.earlycheckin.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.Room;
import com.urbanstay.earlycheckin.domain.RoomEvent;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomStatusChange;
import com.urbanstay.earlycheckin.domain.RoomStatusChangeRepository;
import com.urbanstay.earlycheckin.evaluation.RuleEvaluationService;

import lombok.RequiredArgsConstructor;

/**
 * 객실 이벤트(퇴실, 청소 시작·완료). 상태를 바꾸고 타임라인에 남긴 뒤 규칙을 다시 평가한다.
 */
@Service
@RequiredArgsConstructor
public class RoomEventService {

	private final Clock clock;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final RoomStatusChangeRepository roomStatusChangeRepository;

	private final RuleEvaluationService evaluationService;

	/** 이전 투숙객 퇴실 (퇴실 감지 목업). 연박 중 중도 퇴실은 다루지 않는다. */
	@Transactional
	public void checkOut(String roomNumber) {
		LocalDateTime now = LocalDateTime.now(clock);
		Room room = find(roomNumber);
		Reservation departing = departingGuest(room, now.toLocalDate())
			.orElseThrow(() -> new IllegalStateException(roomNumber + "호에 오늘 퇴실할 투숙객이 없습니다."));
		departing.checkOut(now);
		change(room, Room::checkOut, RoomEvent.CHECK_OUT, departing.getGuestName(), now);
		evaluationService.evaluate();
	}

	@Transactional
	public void startCleaning(String roomNumber) {
		LocalDateTime now = LocalDateTime.now(clock);
		change(find(roomNumber), Room::startCleaning, RoomEvent.CLEANING_STARTED, null, now);
		evaluationService.evaluate();
	}

	@Transactional
	public void finishCleaning(String roomNumber) {
		LocalDateTime now = LocalDateTime.now(clock);
		change(find(roomNumber), room -> room.finishCleaning(now), RoomEvent.CLEANING_FINISHED, null, now);
		evaluationService.evaluate();
	}

	/** 오늘 퇴실 예정이고 아직 투숙 중인 예약 */
	private Optional<Reservation> departingGuest(Room room, LocalDate today) {
		return reservationRepository.findStayingOn(today)
			.stream()
			.filter(r -> r.getRoom() != null && r.getRoom().getNumber().equals(room.getNumber()))
			.filter(r -> r.getCheckOutDate().equals(today) && r.getStatus() == ReservationStatus.CHECKED_IN)
			.findFirst();
	}

	private void change(Room room, Consumer<Room> transition, RoomEvent event, String guestName, LocalDateTime now) {
		RoomStatus from = room.getStatus();
		transition.accept(room);
		roomStatusChangeRepository
			.save(new RoomStatusChange(room.getNumber(), from, room.getStatus(), event, now, guestName));
	}

	private Room find(String roomNumber) {
		return roomRepository.findById(roomNumber)
			.orElseThrow(() -> new NoSuchElementException(roomNumber + "호를 찾을 수 없습니다."));
	}

}
