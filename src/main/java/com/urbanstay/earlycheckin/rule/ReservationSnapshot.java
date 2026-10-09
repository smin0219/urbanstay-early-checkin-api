package com.urbanstay.earlycheckin.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomType;

/** 규칙 판단에 필요한 예약 값. {@code roomNumber}가 null이면 미배정이다. */
public record ReservationSnapshot(
		long id,
		RoomType roomType,
		List<RoomRequest> requests,
		String roomNumber,
		ReservationStatus status,
		LocalDate checkInDate,
		LocalDate checkOutDate,
		LocalTime checkOutTime,
		LocalDateTime checkedOutAt,
		LocalDateTime noonNoticeSentAt,
		OfferStatus offerStatus,
		LocalDateTime earlyCheckInAt,
		LocalDateTime passwordHeldAt,
		LocalDateTime delayNoticeSentAt) {

	public LocalDateTime scheduledCheckOut() {
		return checkOutDate.atTime(checkOutTime);
	}

}
