package com.urbanstay.earlycheckin.rule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.rule.NextStep.Actor;
import com.urbanstay.earlycheckin.rule.OperatorAlert.Level;
import com.urbanstay.earlycheckin.rule.RuleAction.ExpireOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.HoldPassword;
import com.urbanstay.earlycheckin.rule.RuleAction.SendDelayNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendEarlyOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.SendNoonNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendPassword;

/**
 * 체크인 규칙 엔진. 현재 시각과 객실·예약 상태만 보고 판단하며, 스프링·DB·시스템 시계에 의존하지 않는다.
 */
public final class CheckInRuleEngine {

	private final CheckInPolicy policy;

	public CheckInRuleEngine(CheckInPolicy policy) {
		this.policy = policy;
	}

	/**
	 * 지금 실행해야 할 일을 예약당 최대 한 건씩 돌려준다. 적용하면 상태가 바뀌어 다음 일이 생길 수 있으므로,
	 * 호출한 쪽은 빈 목록이 나올 때까지 적용과 평가를 반복한다.
	 */
	public List<RuleAction> evaluate(RuleContext ctx) {
		List<RuleAction> actions = new ArrayList<>();
		for (ReservationSnapshot arrival : ctx.pendingArrivals()) {
			ctx.roomOf(arrival).flatMap(room -> decide(ctx.now(), arrival, room)).ifPresent(actions::add);
		}
		return actions;
	}

	private Optional<RuleAction> decide(LocalDateTime now, ReservationSnapshot r, RoomSnapshot room) {
		LocalDate today = now.toLocalDate();
		long id = r.id();
		if (r.noonNoticeSentAt() == null) {
			if (now.isBefore(today.atTime(policy.noonNoticeAt()))) {
				return Optional.empty();
			}
			return Optional.of(new SendNoonNotice(id, canOffer(now, r, room)));
		}
		RuleAction action = switch (r.status()) {
			case OFFER_SENT -> now.isAfter(today.atTime(policy.lastEarlyCheckInAt())) ? new ExpireOffer(id) : null;
			case ASSIGNED -> {
				if (canOffer(now, r, room)) {
					yield new SendEarlyOffer(id);
				}
				if (now.isBefore(today.atTime(policy.passwordSendAt()))) {
					yield null;
				}
				yield room.isReady() ? new SendPassword(id) : new HoldPassword(id);
			}
			case EARLY_CONFIRMED -> room.isReady() && !now.isBefore(r.earlyCheckInAt()) ? new SendPassword(id) : null;
			case PASSWORD_HELD -> {
				if (room.isReady()) {
					yield new SendPassword(id);
				}
				boolean overdue = !now.isBefore(today.atTime(policy.standardCheckInAt()));
				yield overdue && r.delayNoticeSentAt() == null ? new SendDelayNotice(id) : null;
			}
			default -> null;
		};
		return Optional.ofNullable(action);
	}

	/**
	 * 얼리체크인 제안 조건: 배정된 오늘 예약, 객실 준비 완료, 정오 안내 시각 이후, 입실 가능 시각(준비 완료와 현재 중
	 * 늦은 쪽)이 마지막 얼리 입실 시각 이전, 이 예약에 보낸 제안 없음.
	 */
	private boolean canOffer(LocalDateTime now, ReservationSnapshot r, RoomSnapshot room) {
		LocalDate today = now.toLocalDate();
		if (r.status() != ReservationStatus.ASSIGNED || r.offerStatus() != null || !room.isReady()) {
			return false;
		}
		if (now.isBefore(today.atTime(policy.noonNoticeAt()))) {
			return false;
		}
		LocalDateTime availableAt = latest(room.readyAt(), now);
		return !availableAt.isAfter(today.atTime(policy.lastEarlyCheckInAt()));
	}

	/** 지금 떠 있어야 할 운영자 알림. 유형 순서, 발생 시각 순으로 정렬한다. */
	public List<OperatorAlert> alerts(RuleContext ctx) {
		LocalDateTime now = ctx.now();
		LocalDateTime noon = ctx.today().atTime(policy.noonNoticeAt());
		LocalDateTime warningAt = ctx.today().atTime(policy.cleaningWarningAt());
		LocalDateTime standardCheckIn = ctx.today().atTime(policy.standardCheckInAt());

		List<OperatorAlert> alerts = new ArrayList<>();
		for (ReservationSnapshot arrival : ctx.pendingArrivals()) {
			Optional<RoomSnapshot> assigned = ctx.roomOf(arrival);
			if (assigned.isEmpty()) {
				if (!now.isBefore(noon)) {
					alerts.add(new OperatorAlert(OperatorAlert.Type.UNASSIGNED, Level.WARNING, null, arrival.id(), noon));
				}
				continue;
			}
			RoomSnapshot room = assigned.get();
			earlyCheckout(ctx, arrival, room).ifPresent(alerts::add);
			if (!room.isReady() && !now.isBefore(warningAt)) {
				boolean overdue = !now.isBefore(standardCheckIn);
				alerts.add(new OperatorAlert(OperatorAlert.Type.CLEANING_DELAY, overdue ? Level.CRITICAL : Level.WARNING,
						room.number(), arrival.id(), overdue ? standardCheckIn : warningAt));
			}
			if (arrival.status() == ReservationStatus.PASSWORD_HELD) {
				alerts.add(new OperatorAlert(OperatorAlert.Type.PASSWORD_HELD, Level.CRITICAL, room.number(),
						arrival.id(), arrival.passwordHeldAt()));
			}
		}
		alerts.sort(Comparator.comparing(OperatorAlert::type)
			.thenComparing(OperatorAlert::since, Comparator.nullsLast(Comparator.naturalOrder())));
		return alerts;
	}

	/** 이전 투숙객이 예정보다 기준 이상 일찍 퇴실했고 아직 청소를 시작하지 않은 객실 */
	private Optional<OperatorAlert> earlyCheckout(RuleContext ctx, ReservationSnapshot arrival, RoomSnapshot room) {
		if (room.status() != RoomStatus.CLEANING_PENDING) {
			return Optional.empty();
		}
		return ctx.departureFrom(room.number())
			.filter(d -> d.status() == ReservationStatus.CHECKED_OUT && d.checkedOutAt() != null)
			.filter(d -> !d.checkedOutAt().isAfter(d.scheduledCheckOut().minus(policy.earlyCheckoutThreshold())))
			.map(d -> new OperatorAlert(OperatorAlert.Type.EARLY_CHECKOUT, Level.NOTICE, room.number(), arrival.id(),
					d.checkedOutAt()));
	}

	/** 입실까지 다음에 일어나야 할 일. 입실·퇴실한 예약은 빈 값. */
	public Optional<NextStep> nextStep(RuleContext ctx, ReservationSnapshot r) {
		if (r.status() == ReservationStatus.CHECKED_IN || r.status() == ReservationStatus.CHECKED_OUT) {
			return Optional.empty();
		}
		LocalDateTime now = ctx.now();
		LocalDate day = r.checkInDate();
		Optional<RoomSnapshot> assigned = ctx.roomOf(r);
		if (assigned.isEmpty()) {
			return Optional.of(new NextStep(NextStep.Type.ASSIGN_ROOM, Actor.OPERATOR,
					upcoming(now, day.atTime(policy.noonNoticeAt()))));
		}
		RoomSnapshot room = assigned.get();
		return Optional.of(switch (r.status()) {
			case PASSWORD_SENT -> new NextStep(NextStep.Type.GUEST_ARRIVAL, Actor.GUEST, expectedCheckIn(r));
			case OFFER_SENT -> new NextStep(NextStep.Type.GUEST_PAYMENT, Actor.GUEST,
					day.atTime(policy.lastEarlyCheckInAt()));
			default -> prepareOrSend(ctx, r, room);
		});
	}

	private NextStep prepareOrSend(RuleContext ctx, ReservationSnapshot r, RoomSnapshot room) {
		LocalDateTime now = ctx.now();
		LocalDate day = r.checkInDate();
		switch (room.status()) {
			case OUT_OF_SERVICE -> {
				return new NextStep(NextStep.Type.ASSIGN_ROOM, Actor.OPERATOR, null);
			}
			case OCCUPIED -> {
				LocalDateTime checkOut = ctx.departureFrom(room.number())
					.map(ReservationSnapshot::scheduledCheckOut)
					.orElse(null);
				return new NextStep(NextStep.Type.WAIT_CHECKOUT, Actor.GUEST, checkOut);
			}
			case CLEANING_PENDING -> {
				return new NextStep(NextStep.Type.START_CLEANING, Actor.OPERATOR, cleaningDeadline(now));
			}
			case CLEANING -> {
				return new NextStep(NextStep.Type.FINISH_CLEANING, Actor.OPERATOR, cleaningDeadline(now));
			}
			case READY -> {
				// 아래에서 발송 단계를 판단한다
			}
		}
		if (r.noonNoticeSentAt() == null) {
			return new NextStep(NextStep.Type.NOON_NOTICE, Actor.SYSTEM, day.atTime(policy.noonNoticeAt()));
		}
		if (r.status() == ReservationStatus.EARLY_CONFIRMED) {
			return new NextStep(NextStep.Type.SEND_PASSWORD, Actor.SYSTEM, r.earlyCheckInAt());
		}
		return new NextStep(NextStep.Type.SEND_PASSWORD, Actor.SYSTEM,
				latest(day.atTime(policy.passwordSendAt()), now));
	}

	/** 청소를 끝내야 하는 다음 기준 시각: 경고 시각, 지났으면 비밀번호 발송 시각 */
	private LocalDateTime cleaningDeadline(LocalDateTime now) {
		LocalDate today = now.toLocalDate();
		for (LocalTime deadline : List.of(policy.cleaningWarningAt(), policy.passwordSendAt())) {
			if (now.isBefore(today.atTime(deadline))) {
				return today.atTime(deadline);
			}
		}
		return null;
	}

	/** 고객이 입실할 시각: 얼리 확정이면 결제한 입실 시각, 아니면 기본 체크인 시각 */
	public LocalDateTime expectedCheckIn(ReservationSnapshot r) {
		if (r.offerStatus() == OfferStatus.ACCEPTED && r.earlyCheckInAt() != null) {
			return r.earlyCheckInAt();
		}
		return r.checkInDate().atTime(policy.standardCheckInAt());
	}

	/**
	 * {@code from}과 {@code to} 사이(양 끝 제외)에서 규칙을 평가해야 하는 시각. 시계를 크게 건너뛰어도 각 시점의
	 * 판단과 발송 시각이 그대로 남도록, 시계 이동 시 이 시각들을 차례로 거쳐 간다.
	 */
	public List<LocalDateTime> checkpoints(RuleContext ctx, LocalDateTime from, LocalDateTime to) {
		TreeSet<LocalDateTime> times = new TreeSet<>();
		List<LocalTime> policyTimes = List.of(policy.noonNoticeAt(), policy.cleaningWarningAt(),
				policy.passwordSendAt(), policy.standardCheckInAt());
		for (LocalDate day = from.toLocalDate(); !day.isAfter(to.toLocalDate()); day = day.plusDays(1)) {
			for (LocalTime time : policyTimes) {
				times.add(day.atTime(time));
			}
		}
		ctx.reservations().stream()
			.filter(r -> r.status() == ReservationStatus.EARLY_CONFIRMED && r.earlyCheckInAt() != null)
			.forEach(r -> times.add(r.earlyCheckInAt()));
		return times.stream().filter(t -> t.isAfter(from) && t.isBefore(to)).toList();
	}

	private static LocalDateTime upcoming(LocalDateTime now, LocalDateTime time) {
		return now.isBefore(time) ? time : null;
	}

	private static LocalDateTime latest(LocalDateTime a, LocalDateTime b) {
		if (a == null) {
			return b;
		}
		return a.isAfter(b) ? a : b;
	}

}
