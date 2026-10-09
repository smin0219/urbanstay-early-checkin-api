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

	/** 이전 투숙객 퇴실 */
	public void checkOut() {
		transition(RoomStatus.OCCUPIED, RoomStatus.CLEANING_PENDING);
	}

	public void startCleaning() {
		transition(RoomStatus.CLEANING_PENDING, RoomStatus.CLEANING);
	}

	public void finishCleaning(LocalDateTime now) {
		transition(RoomStatus.CLEANING, RoomStatus.READY);
		this.readyAt = now;
	}

	/** 준비 완료 후 문제 발견 → 다시 청소 */
	public void reportIssue() {
		transition(RoomStatus.READY, RoomStatus.CLEANING);
	}

	/** 도어락 첫 사용으로 고객 입실 */
	public void occupy() {
		transition(RoomStatus.READY, RoomStatus.OCCUPIED);
	}

	private void transition(RoomStatus from, RoomStatus to) {
		if (status != from) {
			throw new IllegalStateException(number + "호의 현재 상태(" + status + ")에서는 할 수 없는 작업입니다.");
		}
		this.status = to;
	}

}
