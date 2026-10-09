package com.urbanstay.earlycheckin.guest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.rule.EarlyCheckInOption;

/**
 * 고객 얼리체크인 화면. 고객이 받은 안내 범위만 담는다 (호수는 정오 안내 후, 비밀번호는 발송 후에만).
 *
 * @param room 정오 안내 후에만
 * @param offer 결제를 기다리는 제안이 있을 때만
 * @param earlyCheckIn 얼리체크인을 결제했을 때만
 * @param offerStatus 제안 결과 (만료·거절·환불 안내용)
 * @param passwordAt 비밀번호를 보낼 예정 시각 (얼리 확정이면 입실 시각, 아니면 비밀번호 발송 시각)
 * @param password 비밀번호 발송 후에만
 * @param delayed 기본 체크인 시각이 지나도 객실이 준비되지 않아 지연 안내를 받음
 */
public record GuestCheckInResponse(LocalDateTime now, String guestName, LocalDate checkInDate, LocalDate checkOutDate,
		LocalDateTime standardCheckInAt, Stage stage, GuestRoom room, Offer offer, EarlyCheckIn earlyCheckIn,
		OfferStatus offerStatus, LocalDateTime passwordAt, Password password, boolean delayed,
		LocalDateTime checkedInAt) {

	public enum Stage {
		/** 정오 안내 전 */
		BEFORE_NOTICE,
		/** 얼리체크인 제안을 받아 결제할 수 있음 */
		OFFER_OPEN,
		/** 얼리체크인 결제 완료, 입실 시각에 비밀번호 발송 */
		EARLY_CONFIRMED,
		/** 비밀번호 발송 시각을 기다림 */
		WAITING_PASSWORD,
		/** 객실 준비가 늦어져 비밀번호 보류 */
		PASSWORD_HELD,
		/** 비밀번호 발송, 입실 가능 */
		PASSWORD_READY,
		CHECKED_IN
	}

	public record GuestRoom(String number, int floor) {
	}

	/** @param deadline 이 시각까지 결제할 수 있다 */
	public record Offer(List<EarlyCheckInOption> options, LocalDateTime deadline) {
	}

	public record EarlyCheckIn(LocalDateTime checkInAt, int fee, LocalDateTime paidAt) {
	}

	public record Password(String doorCode, LocalDateTime sentAt) {
	}

}
