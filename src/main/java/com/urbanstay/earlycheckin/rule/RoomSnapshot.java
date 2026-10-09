package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

import com.urbanstay.earlycheckin.domain.RoomStatus;

public record RoomSnapshot(String number, RoomStatus status, LocalDateTime readyAt) {

	public boolean isReady() {
		return status == RoomStatus.READY;
	}

}
