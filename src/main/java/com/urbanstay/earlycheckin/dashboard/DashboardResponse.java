package com.urbanstay.earlycheckin.dashboard;

import java.time.LocalDateTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.rule.NextStep;
import com.urbanstay.earlycheckin.rule.OperatorAlert;

public record DashboardResponse(LocalDateTime now, Summary summary, List<AlertItem> alerts, List<ArrivalRow> arrivals) {

	/**
	 * 요약 카드. 객실 준비 현황(퇴실 전, 청소, 준비 완료)은 아직 입실하지 않은 오늘 예약의 배정 객실 기준이다.
	 *
	 * @param passwordsToUnreadyRooms 준비 완료가 아닌 객실에 나간 비밀번호 (항상 0이어야 한다)
	 */
	public record Summary(int arrivals, int checkedIn, int pendingAssignment, int awaitingCheckout, int cleaning,
			int ready, int earlyOffered, int earlyConfirmed, long earlyRevenue, long passwordsToUnreadyRooms) {
	}

	/** @param checkInAt 영향을 받는 예약의 입실 예정 시각 */
	public record AlertItem(OperatorAlert.Type type, OperatorAlert.Level level, LocalDateTime since,
			String roomNumber, long reservationId, String guestName, LocalDateTime checkInAt) {
	}

	/** @param checkInAt 입실 예정 시각 (얼리 확정이면 결제한 시각) */
	public record ArrivalRow(long id, String guestName, RoomType roomType, List<RoomRequest> requests,
			String roomNumber, RoomStatus roomStatus, LocalDateTime checkInAt, ReservationStatus status,
			OfferStatus offerStatus, LocalDateTime earlyCheckInAt, Integer earlyCheckInFee, NextStep nextStep) {
	}

}
