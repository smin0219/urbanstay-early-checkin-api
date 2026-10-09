package com.urbanstay.earlycheckin.domain;

public enum OfferStatus {
	/** 제안 발송, 결제 대기 */
	SENT,
	/** 결제 완료 */
	ACCEPTED,
	DECLINED,
	EXPIRED,
	/** 결제 후 객실 문제로 취소·환불 */
	REFUNDED
}
