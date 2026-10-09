package com.urbanstay.earlycheckin.domain;

public enum ReservationStatus {
	/** 배정 대기 */
	PENDING_ASSIGNMENT,
	/** 배정됨 */
	ASSIGNED,
	/** 얼리체크인 제안 발송 */
	OFFER_SENT,
	/** 얼리체크인 결제 완료 */
	EARLY_CONFIRMED,
	/** 비밀번호 발송 */
	PASSWORD_SENT,
	/** 객실 미준비로 비밀번호 보류 */
	PASSWORD_HELD,
	/** 입실 (도어락 첫 사용) */
	CHECKED_IN,
	/** 퇴실 */
	CHECKED_OUT
}
