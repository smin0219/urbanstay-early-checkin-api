package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

/**
 * 예약이 입실까지 가기 위해 다음에 일어나야 할 일.
 *
 * @param dueAt 예정·기한 시각 (없으면 null)
 */
public record NextStep(Type type, Actor actor, LocalDateTime dueAt) {

	public enum Type {
		ASSIGN_ROOM,
		WAIT_CHECKOUT,
		START_CLEANING,
		FINISH_CLEANING,
		NOON_NOTICE,
		GUEST_PAYMENT,
		SEND_PASSWORD,
		GUEST_ARRIVAL
	}

	public enum Actor {
		OPERATOR,
		SYSTEM,
		GUEST
	}

}
