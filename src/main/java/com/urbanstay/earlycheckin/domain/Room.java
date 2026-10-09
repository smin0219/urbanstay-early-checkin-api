package com.urbanstay.earlycheckin.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

	/** 호수. 화면 경로(/rooms/{number})에도 그대로 쓴다. */
	@Id
	private String number;

	@Enumerated(EnumType.STRING)
	private RoomType type;

	private int floor;

	@Enumerated(EnumType.STRING)
	private RoomView view;

	@Enumerated(EnumType.STRING)
	private RoomStatus status;

	/** 마지막으로 준비 완료가 된 시각 */
	private LocalDateTime readyAt;

	public Room(String number, RoomType type, int floor, RoomView view, RoomStatus status, LocalDateTime readyAt) {
		this.number = number;
		this.type = type;
		this.floor = floor;
		this.view = view;
		this.status = status;
		this.readyAt = readyAt;
	}

	public boolean isReady() {
		return status == RoomStatus.READY;
	}

}
