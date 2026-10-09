package com.urbanstay.earlycheckin.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 객실 상태 타임라인 한 줄 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomStatusChange {

	@Id
	@GeneratedValue
	private Long id;

	private String roomNumber;

	/** 시나리오 시작 기록이면 null */
	@Enumerated(EnumType.STRING)
	private RoomStatus fromStatus;

	@Enumerated(EnumType.STRING)
	private RoomStatus toStatus;

	@Enumerated(EnumType.STRING)
	private RoomEvent event;

	private LocalDateTime changedAt;

	/** 퇴실·입실한 투숙객 */
	private String guestName;

	public RoomStatusChange(String roomNumber, RoomStatus fromStatus, RoomStatus toStatus, RoomEvent event,
			LocalDateTime changedAt, String guestName) {
		this.roomNumber = roomNumber;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.event = event;
		this.changedAt = changedAt;
		this.guestName = guestName;
	}

}
