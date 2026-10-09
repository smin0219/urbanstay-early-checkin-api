package com.urbanstay.earlycheckin.rule;

/** 규칙 엔진이 돌려주는 할 일. 적용(상태 변경, 발송 기록)은 호출한 쪽이 한다. */
public sealed interface RuleAction {

	long reservationId();

	/** 정오 안내 (호수·입실 방법). {@code withOffer}면 얼리체크인 제안을 함께 담는다. 비밀번호는 넣지 않는다. */
	record SendNoonNotice(long reservationId, boolean withOffer) implements RuleAction {
	}

	/** 정오 이후 객실이 준비되어 따로 보내는 얼리체크인 제안 */
	record SendEarlyOffer(long reservationId) implements RuleAction {
	}

	record ExpireOffer(long reservationId) implements RuleAction {
	}

	record SendPassword(long reservationId) implements RuleAction {
	}

	record HoldPassword(long reservationId) implements RuleAction {
	}

	/** 기본 체크인 시각이 지나도 미준비일 때 고객 지연 안내 */
	record SendDelayNotice(long reservationId) implements RuleAction {
	}

}
