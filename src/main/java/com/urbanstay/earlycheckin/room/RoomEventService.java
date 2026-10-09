package com.urbanstay.earlycheckin.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.domain.MessageLog;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
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
 * 객실 이벤트(퇴실, 청소 시작·완료, 문제 발견, 고객 입실). 상태를 바꾸고 타임라인에 남긴 뒤 규칙을 다시 평가한다.
 */
@Service
@RequiredArgsConstructor
public class RoomEventService {

	private final Clock clock;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final RoomStatusChangeRepository roomStatusChangeRepository;

	private final MessageLogRepository messageLogRepository;

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

	/**
	 * 준비 완료 후 문제 발견: 객실을 다시 청소 중으로 돌리고, 얼리체크인을 결제한 예약은 취소·환불(목업)한다. 이미 비밀번호가 나간
	 * 객실은 고객에게 직접 연락해야 하므로 받지 않는다.
	 */
	@Transactional
	public void reportIssue(String roomNumber) {
		LocalDateTime now = LocalDateTime.now(clock);
		Room room = find(roomNumber);
		Optional<Reservation> arrival = arrivalTo(room, now.toLocalDate());
		if (arrival.map(r -> r.getStatus() == ReservationStatus.PASSWORD_SENT).orElse(false)) {
			throw new IllegalStateException(roomNumber + "호는 비밀번호가 이미 발송되어 고객에게 직접 연락해야 합니다.");
		}
		change(room, Room::reportIssue, RoomEvent.ISSUE_REPORTED, null, now);
		arrival.filter(r -> r.getStatus() == ReservationStatus.EARLY_CONFIRMED).ifPresent(r -> {
			r.refundEarlyCheckIn(now);
			messageLogRepository.save(new MessageLog(r, MessageType.EARLY_CHECKIN_REFUND, now, "[어반스테이] " + roomNumber
					+ "호 점검이 필요해 얼리체크인을 취소하고 " + r.getEarlyCheckInFee() + "원을 환불했습니다. 준비가 끝나면 비밀번호를 보내드립니다."));
		});
		evaluationService.evaluate();
	}

	/** 도어락 첫 사용으로 고객이 입실한다. */
	@Transactional
	public void checkIn(Reservation guest) {
		LocalDateTime now = LocalDateTime.now(clock);
		guest.checkIn(now);
		change(guest.getRoom(), Room::occupy, RoomEvent.CHECK_IN, guest.getGuestName(), now);
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

	private Optional<Reservation> arrivalTo(Room room, LocalDate today) {
		return reservationRepository.findStayingOn(today)
			.stream()
			.filter(r -> r.getRoom() != null && r.getRoom().getNumber().equals(room.getNumber()))
			.filter(r -> r.getCheckInDate().equals(today) && r.getStatus() != ReservationStatus.CHECKED_OUT)
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
