package com.urbanstay.earlycheckin.evaluation;

import java.time.LocalDateTime;
import java.util.Collection;

import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.Room;
import com.urbanstay.earlycheckin.rule.ReservationSnapshot;
import com.urbanstay.earlycheckin.rule.RoomSnapshot;
import com.urbanstay.earlycheckin.rule.RuleContext;

/** 엔티티를 규칙 엔진 입력으로 옮긴다. */
public final class RuleContexts {

	private RuleContexts() {
	}

	public static RuleContext of(LocalDateTime now, Collection<Room> rooms, Collection<Reservation> reservations) {
		return RuleContext.of(now,
				rooms.stream().map(RuleContexts::snapshot).toList(),
				reservations.stream().map(RuleContexts::snapshot).toList());
	}

	public static RoomSnapshot snapshot(Room room) {
		return new RoomSnapshot(room.getNumber(), room.getStatus(), room.getReadyAt());
	}

	public static ReservationSnapshot snapshot(Reservation r) {
		return new ReservationSnapshot(
				r.getId(),
				r.getRoom() == null ? null : r.getRoom().getNumber(),
				r.getStatus(),
				r.getCheckInDate(),
				r.getCheckOutDate(),
				r.getCheckOutTime(),
				r.getCheckedOutAt(),
				r.getNoonNoticeSentAt(),
				r.getOfferStatus(),
				r.getEarlyCheckInAt(),
				r.getPasswordHeldAt(),
				r.getDelayNoticeSentAt());
	}

}
