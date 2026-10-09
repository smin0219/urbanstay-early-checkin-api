package com.urbanstay.earlycheckin.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomEvent;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;
import com.urbanstay.earlycheckin.rule.NextStep;
import com.urbanstay.earlycheckin.rule.OperatorAlert;
import com.urbanstay.earlycheckin.rule.RuleCheck;

/**
 * 객실 운영 상세.
 *
 * @param inHouse 지금 투숙 중인 다른 예약 (연박 등). 오늘 입실한 예약은 {@code arrival}에 담는다
 * @param departure 오늘 퇴실하는(또는 퇴실한) 이전 투숙객
 * @param arrival 오늘 입실 예약
 * @param actions 지금 누를 수 있는 운영 버튼
 * @param ruleChecks 규칙 엔진 판단 근거 (조건별 충족 여부)
 * @param alerts 이 객실의 운영자 알림
 */
public record RoomDetailResponse(LocalDateTime now, RoomInfo room, Stay inHouse, Stay departure, Arrival arrival,
		List<Action> actions, List<RuleCheck> ruleChecks, List<OperatorAlert> alerts, List<TimelineEntry> timeline,
		List<MessageEntry> messages) {

	public record RoomInfo(String number, RoomType type, int floor, RoomView view, RoomStatus status,
			LocalDateTime readyAt) {
	}

	/** @param scheduledCheckOut 예정 퇴실 시각 (레이트 체크아웃 반영) */
	public record Stay(long reservationId, String guestName, LocalDate checkInDate, LocalDate checkOutDate,
			LocalDateTime scheduledCheckOut, ReservationStatus status, LocalDateTime checkedOutAt) {
	}

	/** @param checkInToken 고객 화면(/check-in/{token}) 링크 */
	public record Arrival(long reservationId, String guestName, ReservationStatus status, LocalDateTime checkInAt,
			OfferStatus offerStatus, LocalDateTime earlyCheckInAt, Integer earlyCheckInFee, LocalDateTime checkedInAt,
			NextStep nextStep, String checkInToken) {
	}

	public enum Action {
		CHECK_OUT,
		START_CLEANING,
		FINISH_CLEANING
	}

	/** @param from 시나리오 시작 기록이면 null */
	public record TimelineEntry(LocalDateTime at, RoomEvent event, RoomStatus from, RoomStatus to, String guestName) {
	}

	/** @param roomStatus 발송 시점의 객실 상태 */
	public record MessageEntry(LocalDateTime sentAt, MessageType type, long reservationId, String guestName,
			RoomStatus roomStatus, String content) {
	}

}
