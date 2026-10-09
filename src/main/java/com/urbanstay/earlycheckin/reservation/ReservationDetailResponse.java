package com.urbanstay.earlycheckin.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Reason;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.RequestMatch;
import com.urbanstay.earlycheckin.rule.NextStep;
import com.urbanstay.earlycheckin.rule.OperatorAlert;
import com.urbanstay.earlycheckin.rule.RoomAssignmentRule;

/** @param alerts 이 예약에 걸린 운영자 알림 */
public record ReservationDetailResponse(LocalDateTime now, Detail reservation, List<OperatorAlert> alerts,
		Assignment assignment) {

	/**
	 * @param room 배정(또는 투숙) 객실. 미배정이면 null
	 * @param checkInAt 입실 예정 시각 (얼리 확정이면 결제한 시각)
	 */
	public record Detail(long id, String guestName, RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate,
			LocalTime checkOutTime, int nights, List<RoomRequest> requests, ReservationStatus status, RoomInfo room,
			LocalDateTime assignedAt, LocalDateTime checkInAt, OfferStatus offerStatus, LocalDateTime earlyCheckInAt,
			Integer earlyCheckInFee, NextStep nextStep) {
	}

	public record RoomInfo(String number, RoomType type, int floor, RoomView view, RoomStatus status,
			LocalDateTime readyAt) {
	}

	/**
	 * @param lock 배정을 바꿀 수 없는 이유. 바꿀 수 있으면 null이고, 잠겨 있으면 후보를 계산하지 않는다
	 * @param candidates 추천 순서
	 */
	public record Assignment(RoomAssignmentRule.Lock lock, List<CandidateItem> candidates,
			List<ExcludedItem> excluded) {
	}

	/** @param current 지금 이 예약에 배정된 객실 */
	public record CandidateItem(RoomInfo room, List<RequestMatch> matches, long matchCount, boolean vacantLastNight,
			boolean current) {
	}

	public record ExcludedItem(RoomInfo room, List<ExclusionItem> reasons) {
	}

	/** @param conflict 기간이 겹치는 예약 (겹침이 아니면 null) */
	public record ExclusionItem(Reason reason, ConflictingStay conflict) {
	}

	public record ConflictingStay(long reservationId, String guestName, LocalDate checkInDate,
			LocalDate checkOutDate) {
	}

}
