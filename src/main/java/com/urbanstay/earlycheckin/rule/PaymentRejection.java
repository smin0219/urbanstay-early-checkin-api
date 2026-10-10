package com.urbanstay.earlycheckin.rule;

/** 얼리체크인 결제를 받을 수 없는 이유 */
public enum PaymentRejection {
	/** 마지막 얼리 입실 정각이 되어 제안이 만료됨 */
	OFFER_EXPIRED,
	/** 결제를 기다리는 제안이 없음 (미발송, 거절, 이미 결제) */
	OFFER_NOT_OPEN,
	/** 고른 입실 정각이 되었거나 지남 (정각은 그 전까지, "지금 바로"는 정오 전까지 결제) */
	TIME_PASSED,
	/** 선택지에 없는 입실 시각 (정각이 아니거나, 첫 입실 정각 전이거나, 마지막 얼리 입실 정각 이후) */
	INVALID_TIME
}
