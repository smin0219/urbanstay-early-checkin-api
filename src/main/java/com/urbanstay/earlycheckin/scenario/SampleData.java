package com.urbanstay.earlycheckin.scenario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;

/** sample-data.json 형식 */
record SampleData(List<RoomData> rooms, List<ReservationData> reservations) {

	record RoomData(String number, RoomType type, int floor, RoomView view, RoomStatus status,
			LocalDateTime readyAt) {
	}

	/**
	 * @param roomNumber 배정(또는 투숙 중) 객실. 없으면 미배정
	 * @param checkOutTime 예정 퇴실 시각. 없으면 정책의 기본 퇴실 시각
	 * @param checkedOutAt 이미 퇴실한 경우 퇴실 시각
	 */
	record ReservationData(long id, String guestName, RoomType roomType, LocalDate checkInDate,
			LocalDate checkOutDate, LocalTime checkOutTime, List<RoomRequest> requests, String roomNumber,
			ReservationStatus status, LocalDateTime checkedOutAt) {
	}

}
