package com.urbanstay.earlycheckin.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고객 문자 발송 기록 (문자 발송 목업). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageLog {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Reservation reservation;

	private String roomNumber;

	@Enumerated(EnumType.STRING)
	private MessageType type;

	private LocalDateTime sentAt;

	/** 발송 시점의 객실 상태. "미준비 객실에 나간 비밀번호 0건" 지표를 이 값으로 검증한다. */
	@Enumerated(EnumType.STRING)
	private RoomStatus roomStatus;

	@Column(length = 500)
	private String content;

	public MessageLog(Reservation reservation, MessageType type, LocalDateTime sentAt, String content) {
		this.reservation = reservation;
		this.roomNumber = reservation.getRoom().getNumber();
		this.roomStatus = reservation.getRoom().getStatus();
		this.type = type;
		this.sentAt = sentAt;
		this.content = content;
	}

}
