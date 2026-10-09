package com.urbanstay.earlycheckin.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.urbanstay.earlycheckin.domain.ReservationStatus;

/** 규칙 평가 입력: 현재 시각과 그 시점의 객실·예약 상태. */
public record RuleContext(LocalDateTime now, Map<String, RoomSnapshot> rooms, List<ReservationSnapshot> reservations) {

	public static RuleContext of(LocalDateTime now, Collection<RoomSnapshot> rooms,
			Collection<ReservationSnapshot> reservations) {
		Map<String, RoomSnapshot> byNumber = rooms.stream()
			.collect(Collectors.toUnmodifiableMap(RoomSnapshot::number, Function.identity()));
		List<ReservationSnapshot> sorted = reservations.stream()
			.sorted(Comparator.comparingLong(ReservationSnapshot::id))
			.toList();
		return new RuleContext(now, byNumber, sorted);
	}

	public LocalDate today() {
		return now.toLocalDate();
	}

	/** 오늘 입실 예정이고 아직 입실하지 않은 예약 */
	public List<ReservationSnapshot> pendingArrivals() {
		return reservations.stream()
			.filter(r -> r.checkInDate().equals(today()))
			.filter(r -> r.status() != ReservationStatus.CHECKED_IN && r.status() != ReservationStatus.CHECKED_OUT)
			.toList();
	}

	public Optional<ReservationSnapshot> reservation(long id) {
		return reservations.stream().filter(r -> r.id() == id).findFirst();
	}

	public Optional<RoomSnapshot> roomOf(ReservationSnapshot reservation) {
		return Optional.ofNullable(reservation.roomNumber()).map(rooms::get);
	}

	/** 이 객실에 배정된 오늘 입실 예약 (이미 입실했어도 포함) */
	public Optional<ReservationSnapshot> arrivalTo(String roomNumber) {
		return reservations.stream()
			.filter(r -> Objects.equals(r.roomNumber(), roomNumber))
			.filter(r -> r.checkInDate().equals(today()))
			.filter(r -> r.status() != ReservationStatus.CHECKED_OUT)
			.findFirst();
	}

	/** 이 객실에서 오늘 퇴실하는(또는 이미 퇴실한) 이전 투숙객 예약 */
	public Optional<ReservationSnapshot> departureFrom(String roomNumber) {
		return reservations.stream()
			.filter(r -> Objects.equals(r.roomNumber(), roomNumber))
			.filter(r -> r.checkOutDate().equals(today()))
			.filter(r -> r.status() == ReservationStatus.CHECKED_IN || r.status() == ReservationStatus.CHECKED_OUT)
			.findFirst();
	}

}
