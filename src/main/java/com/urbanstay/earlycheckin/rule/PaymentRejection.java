package com.urbanstay.earlycheckin.rule;

/** 얼리체크인 결제를 받을 수 없는 이유 */
public enum PaymentRejection {
	/** 마지막 얼리 입실 시각이 지나 제안이 만료됨 */
	OFFER_EXPIRED,
	/** 결제를 기다리는 제안이 없음 (미발송, 거절, 이미 결제) */
	OFFER_NOT_OPEN,
	/** 고른 입실 시각이 이미 지남 */
	TIME_PASSED,
	/** 선택지에 없는 입실 시각 (정각이 아니거나 마지막 얼리 입실 시각 이후) */
	INVALID_TIME
}
