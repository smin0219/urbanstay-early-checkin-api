package com.urbanstay.earlycheckin.domain;

public enum RoomStatus {
	/** 사용 중 (투숙객 체류) */
	OCCUPIED,
	/** 청소 대기 (퇴실 완료) */
	CLEANING_PENDING,
	/** 청소 중 */
	CLEANING,
	/** 준비 완료 */
	READY,
	/** 운영 중지 (수리 등) */
	OUT_OF_SERVICE
}
