package com.urbanstay.earlycheckin.clock;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param milestones 오늘의 규칙 시각. 운영 화면의 시계 타임라인에 눈금으로 표시한다
 */
public record ClockResponse(LocalDateTime now, List<Milestone> milestones) {

	public record Milestone(LocalDateTime at, Type type) {

		public enum Type {
			/** 이미 준비된 방에 얼리체크인 제안 시작 */
			OFFER_START,
			NOON_NOTICE,
			CLEANING_WARNING,
			/** 얼리체크인 제안 만료 */
			OFFER_EXPIRY,
			PASSWORD_SEND,
			STANDARD_CHECK_IN
		}

	}

}
