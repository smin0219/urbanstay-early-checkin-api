package com.urbanstay.earlycheckin.domain;

/** 객실 상태를 바꾼 사건 (상태 타임라인) */
public enum RoomEvent {
	/** 시나리오 시작 시 상태 */
	INITIAL,
	CHECK_OUT,
	CLEANING_STARTED,
	CLEANING_FINISHED,
	ISSUE_REPORTED,
	CHECK_IN
}
