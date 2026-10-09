package com.urbanstay.earlycheckin.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

	/** 샘플 데이터의 id를 그대로 써서 시나리오 초기화 후에도 같은 경로를 유지한다. */
	@Id
	private Long id;

	private String guestName;

	@Enumerated(EnumType.STRING)
	private RoomType roomType;

	private LocalDate checkInDate;

	private LocalDate checkOutDate;

	/** 예정 퇴실 시각 (레이트 체크아웃이면 기본값보다 늦다) */
	private LocalTime checkOutTime;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "reservation_request")
	@Column(name = "room_request")
	@Enumerated(EnumType.STRING)
	private Set<RoomRequest> requests = new LinkedHashSet<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_number")
	private Room room;

	@Enumerated(EnumType.STRING)
	private ReservationStatus status;

	private LocalDateTime assignedAt;

	private LocalDateTime noonNoticeSentAt;

	@Enumerated(EnumType.STRING)
	private OfferStatus offerStatus;

	private LocalDateTime offerSentAt;

	/** 얼리체크인으로 결제한 입실 시각 */
	private LocalDateTime earlyCheckInAt;

	private Integer earlyCheckInFee;

	private LocalDateTime paidAt;

	private LocalDateTime passwordSentAt;

	private LocalDateTime passwordHeldAt;

	private LocalDateTime delayNoticeSentAt;

	private LocalDateTime checkedInAt;

	private LocalDateTime checkedOutAt;

	public Reservation(Long id, String guestName, RoomType roomType, LocalDate checkInDate, LocalDate checkOutDate,
			LocalTime checkOutTime, Collection<RoomRequest> requests, Room room, ReservationStatus status,
			LocalDateTime checkedOutAt) {
		this.id = id;
		this.guestName = guestName;
		this.roomType = roomType;
		this.checkInDate = checkInDate;
		this.checkOutDate = checkOutDate;
		this.checkOutTime = checkOutTime;
		this.requests = new LinkedHashSet<>(requests);
		this.room = room;
		this.status = status;
		this.checkedOutAt = checkedOutAt;
	}

	/** 배정 가능 여부(후보 조건, 안내 발송 후 변경 불가)는 배정 규칙이 판단하고, 여기서는 상태만 확인한다. */
	public void assign(Room room, LocalDateTime now) {
		if (status != ReservationStatus.PENDING_ASSIGNMENT && status != ReservationStatus.ASSIGNED) {
			throw new IllegalStateException("예약 " + id + "은(는) 배정을 바꿀 수 없는 상태입니다: " + status);
		}
		this.room = room;
		this.status = ReservationStatus.ASSIGNED;
		this.assignedAt = now;
	}

	/** 배정을 풀어 배정 대기로 되돌린다. 바꿀 수 있는지(안내 발송 전)는 배정 규칙이 판단한다. */
	public void unassign() {
		requireStatus(ReservationStatus.ASSIGNED);
		this.room = null;
		this.status = ReservationStatus.PENDING_ASSIGNMENT;
		this.assignedAt = null;
	}

	public void sendNoonNotice(LocalDateTime now) {
		this.noonNoticeSentAt = now;
	}

	public void sendOffer(LocalDateTime now) {
		requireStatus(ReservationStatus.ASSIGNED);
		this.status = ReservationStatus.OFFER_SENT;
		this.offerStatus = OfferStatus.SENT;
		this.offerSentAt = now;
	}

	public void expireOffer() {
		requireStatus(ReservationStatus.OFFER_SENT);
		this.status = ReservationStatus.ASSIGNED;
		this.offerStatus = OfferStatus.EXPIRED;
	}

	public void sendPassword(LocalDateTime now) {
		if (room == null || !room.isReady()) {
			throw new IllegalStateException("준비 완료가 아닌 객실에는 비밀번호를 보낼 수 없습니다: 예약 " + id);
		}
		this.status = ReservationStatus.PASSWORD_SENT;
		this.passwordSentAt = now;
	}

	public void holdPassword(LocalDateTime now) {
		this.status = ReservationStatus.PASSWORD_HELD;
		this.passwordHeldAt = now;
	}

	public void sendDelayNotice(LocalDateTime now) {
		this.delayNoticeSentAt = now;
	}

	private void requireStatus(ReservationStatus expected) {
		if (status != expected) {
			throw new IllegalStateException("예약 " + id + " 상태가 " + expected + "가 아닙니다: " + status);
		}
	}

}
