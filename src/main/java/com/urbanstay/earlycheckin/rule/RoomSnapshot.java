package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;

public record RoomSnapshot(String number, RoomType type, int floor, RoomView view, RoomStatus status,
		LocalDateTime readyAt) {

	public boolean isReady() {
		return status == RoomStatus.READY;
	}

}
