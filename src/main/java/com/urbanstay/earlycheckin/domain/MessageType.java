package com.urbanstay.earlycheckin.domain;

public enum MessageType {
	NOON_NOTICE,
	EARLY_OFFER,
	PASSWORD,
	DELAY_NOTICE,
	/** 결제 후 객실 문제로 얼리체크인 취소·환불 안내 */
	EARLY_CHECKIN_REFUND
}
