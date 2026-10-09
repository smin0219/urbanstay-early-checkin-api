package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 규칙 하나를 조건별로 판단한 결과. 운영 화면에서 "왜 제안이 안 나갔는지" 같은 판단 근거를 보여준다.
 * 규칙 엔진의 실제 판단과 같은 조건 함수로 만든다.
 */
public record RuleCheck(Rule rule, Outcome outcome, List<Condition> conditions) {

	public enum Rule {
		EARLY_OFFER,
		PASSWORD,
		CLEANING_WARNING,
		EARLY_CHECKOUT
	}

	public enum Outcome {
		/** 이미 실행됨 (제안·비밀번호 발송) */
		DONE,
		/** 조건 충족 (알림 규칙이면 알림이 떠 있음) */
		MET,
		NOT_MET
	}

	/**
	 * @param actual 비교한 실제 값 (예: 현재 시각, 입실 가능 시각). 시각 비교가 아니면 null
	 * @param threshold 기준 값 (예: 12:00). 시각 비교가 아니면 null
	 */
	public record Condition(Type type, boolean satisfied, LocalDateTime actual, LocalDateTime threshold) {

		static Condition of(Type type, boolean satisfied) {
			return new Condition(type, satisfied, null, null);
		}

		public enum Type {
			CHECK_IN_TODAY,
			ROOM_ASSIGNED,
			ROOM_READY,
			NOON_REACHED,
			AVAILABLE_BY_DEADLINE,
			NO_PREVIOUS_OFFER,
			SEND_TIME_REACHED,
			WARNING_TIME_REACHED,
			ROOM_NOT_READY,
			CHECKED_OUT_EARLY,
			HAS_ARRIVAL_TODAY,
			CLEANING_NOT_STARTED
		}

	}

	static boolean allSatisfied(List<Condition> conditions) {
		return conditions.stream().allMatch(Condition::satisfied);
	}

}
