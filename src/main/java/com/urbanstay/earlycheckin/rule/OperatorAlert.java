package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

/**
 * 운영자 알림. 저장하지 않고 현재 상태에서 매번 계산하므로, 조건이 풀리면(청소 시작, 배정 등) 자동으로 사라진다.
 *
 * @param roomNumber 관련 객실 (미배정 알림이면 null)
 * @param reservationId 영향을 받는 오늘 입실 예약
 * @param since 알림 조건이 성립한 시각
 */
public record OperatorAlert(Type type, Level level, String roomNumber, long reservationId, LocalDateTime since) {

	/** 선언 순서가 알림 목록 정렬 순서다. */
	public enum Type {
		/** 이전 투숙객 조기 퇴실 → 청소 우선 */
		EARLY_CHECKOUT,
		PASSWORD_HELD,
		/** 경고 시각까지 청소 미완료 (기본 체크인 시각이 지나면 단계 상향) */
		CLEANING_DELAY,
		/** 정오 안내 시각에 미배정 → 안내 보류 */
		UNASSIGNED
	}

	public enum Level {
		CRITICAL,
		WARNING,
		NOTICE
	}

}
