package com.urbanstay.earlycheckin.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;
import com.urbanstay.earlycheckin.rule.NextStep.Actor;
import com.urbanstay.earlycheckin.rule.OperatorAlert.Level;
import com.urbanstay.earlycheckin.rule.RuleAction.ExpireOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.HoldPassword;
import com.urbanstay.earlycheckin.rule.RuleAction.SendDelayNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendEarlyOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.SendNoonNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendPassword;

class CheckInRuleEngineTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

	private static final CheckInPolicy POLICY = new CheckInPolicy(LocalTime.of(12, 0), LocalTime.of(14, 0),
			LocalTime.of(13, 30), LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(11, 0),
			Duration.ofMinutes(30), 8, 10_000);

	private final CheckInRuleEngine engine = new CheckInRuleEngine(POLICY);

	/** 이 테스트에서 평가한 컨텍스트. 끝날 때 불변식(미준비 객실에 비밀번호 없음)을 함께 검사한다. */
	private final List<RuleContext> evaluated = new ArrayList<>();

	@AfterEach
	void passwordIsNeverSentToUnreadyRoom() {
		for (RuleContext ctx : evaluated) {
			for (RuleAction action : engine.evaluate(ctx)) {
				if (action instanceof SendPassword) {
					ReservationSnapshot r = ctx.reservation(action.reservationId()).orElseThrow();
					assertThat(ctx.roomOf(r)).hasValueSatisfying(room -> assertThat(room.isReady()).isTrue());
				}
			}
		}
	}

	@Nested
	class 정오_안내 {

		@Test
		void 정오_전에는_아무것도_보내지_않는다() {
			assertThat(evaluate("11:59", readyRoom("1203", "10:00"), arrival(1).in("1203"))).isEmpty();
		}

		@Test
		void 정오에_준비된_방이면_안내에_제안을_담는다() {
			assertThat(evaluate("12:00", readyRoom("1203", "11:10"), arrival(1).in("1203")))
				.containsExactly(new SendNoonNotice(1, true));
		}

		@Test
		void 정오에_미준비_방이면_안내만_보낸다() {
			assertThat(evaluate("12:00", room("803", RoomStatus.CLEANING), arrival(2).in("803")))
				.containsExactly(new SendNoonNotice(2, false));
		}

		@Test
		void 미배정_예약은_안내를_보류하고_운영자에게_알린다() {
			RuleContext ctx = context("12:00", List.of(), arrival(1));

			assertThat(engine.evaluate(ctx)).isEmpty();
			assertThat(engine.alerts(ctx)).singleElement()
				.satisfies(alert -> assertThat(alert.type()).isEqualTo(OperatorAlert.Type.UNASSIGNED));
		}

		@Test
		void 미배정_알림은_정오_전에는_뜨지_않는다() {
			assertThat(engine.alerts(context("11:59", List.of(), arrival(1)))).isEmpty();
		}

	}

	@Nested
	class 얼리체크인_제안 {

		@ParameterizedTest
		@CsvSource({ "13:59, true", "14:00, true", "14:01, false" })
		void 정오_이후_준비되면_14시까지만_즉시_제안한다(String readyAt, boolean offered) {
			List<RuleAction> actions = evaluate(readyAt, readyRoom("1203", readyAt), arrival(1).in("1203").noticed());

			if (offered) {
				assertThat(actions).containsExactly(new SendEarlyOffer(1));
			}
			else {
				assertThat(actions).isEmpty();
			}
		}

		@Test
		void 예약당_한_번만_제안한다() {
			assertThat(evaluate("13:00", readyRoom("1203", "12:30"),
					arrival(1).in("1203").noticed().offer(OfferStatus.EXPIRED)))
				.isEmpty();
		}

		@Test
		void 제안은_14시_정각까지_유효하고_지나면_만료된다() {
			ArrivalBuilder offered = arrival(1).in("1203").noticed().status(ReservationStatus.OFFER_SENT)
				.offer(OfferStatus.SENT);

			assertThat(evaluate("14:00", readyRoom("1203", "11:10"), offered)).isEmpty();
			assertThat(evaluate("14:01", readyRoom("1203", "11:10"), offered)).containsExactly(new ExpireOffer(1));
		}

	}

	@Nested
	class 비밀번호_발송 {

		@Test
		void 준비된_방은_14시30분에_보낸다() {
			ArrivalBuilder assigned = arrival(1).in("1203").noticed().offer(OfferStatus.EXPIRED);

			assertThat(evaluate("14:29", readyRoom("1203", "11:10"), assigned)).isEmpty();
			assertThat(evaluate("14:30", readyRoom("1203", "11:10"), assigned)).containsExactly(new SendPassword(1));
		}

		@Test
		void 미준비_방은_14시30분에_보류한다() {
			assertThat(evaluate("14:30", room("803", RoomStatus.CLEANING), arrival(2).in("803").noticed()))
				.containsExactly(new HoldPassword(2));
		}

		@Test
		void 보류된_예약은_준비_완료_즉시_보낸다() {
			assertThat(evaluate("15:20", readyRoom("803", "15:20"),
					arrival(2).in("803").noticed().status(ReservationStatus.PASSWORD_HELD).held("14:30")))
				.containsExactly(new SendPassword(2));
		}

		@Test
		void 기본_체크인_시각이_되어도_미준비면_지연_안내를_한_번_보낸다() {
			ArrivalBuilder held = arrival(2).in("803").noticed().status(ReservationStatus.PASSWORD_HELD).held("14:30");

			assertThat(evaluate("14:59", room("803", RoomStatus.CLEANING), held)).isEmpty();
			assertThat(evaluate("15:00", room("803", RoomStatus.CLEANING), held))
				.containsExactly(new SendDelayNotice(2));
			assertThat(evaluate("15:10", room("803", RoomStatus.CLEANING), held.delayNoticed("15:00"))).isEmpty();
		}

		@Test
		void 얼리_확정_건은_결제한_입실_시각에_보낸다() {
			ArrivalBuilder confirmed = arrival(1).in("1203").noticed().status(ReservationStatus.EARLY_CONFIRMED)
				.offer(OfferStatus.ACCEPTED).earlyAt("13:00");

			assertThat(evaluate("12:59", readyRoom("1203", "11:10"), confirmed)).isEmpty();
			assertThat(evaluate("13:00", readyRoom("1203", "11:10"), confirmed)).containsExactly(new SendPassword(1));
		}

	}

	@Nested
	class 운영자_경고 {

		@Test
		void 청소_미완료_경고는_13시30분부터_뜬다() {
			ArrivalBuilder assigned = arrival(2).in("803").noticed();

			assertThat(engine.alerts(context("13:29", List.of(room("803", RoomStatus.CLEANING)), assigned))).isEmpty();
			assertThat(engine.alerts(context("13:30", List.of(room("803", RoomStatus.CLEANING)), assigned)))
				.singleElement()
				.satisfies(alert -> {
					assertThat(alert.type()).isEqualTo(OperatorAlert.Type.CLEANING_DELAY);
					assertThat(alert.level()).isEqualTo(Level.WARNING);
				});
		}

		@Test
		void 기본_체크인_시각이_지나면_경고_단계가_오른다() {
			assertThat(engine.alerts(context("15:00", List.of(room("803", RoomStatus.CLEANING)),
					arrival(2).in("803").noticed().status(ReservationStatus.PASSWORD_HELD).held("14:30"))))
				.extracting(OperatorAlert::type, OperatorAlert::level)
				.containsExactly(
						tuple(OperatorAlert.Type.PASSWORD_HELD, Level.CRITICAL),
						tuple(OperatorAlert.Type.CLEANING_DELAY, Level.CRITICAL));
		}

		@Test
		void 준비된_방은_경고하지_않는다() {
			assertThat(engine.alerts(context("13:30", List.of(readyRoom("1203", "11:10")),
					arrival(1).in("1203").noticed()))).isEmpty();
		}

	}

	@Nested
	class 조기_퇴실 {

		@ParameterizedTest
		@CsvSource({ "10:30, true", "10:31, false" })
		void 예정_퇴실보다_30분_이상_일찍_나가면_알린다(String checkedOutAt, boolean alerted) {
			List<OperatorAlert> alerts = engine.alerts(context("10:40", List.of(room("1203", RoomStatus.CLEANING_PENDING)),
					arrival(1).in("1203"), departed(101, "1203", checkedOutAt)));

			assertThat(alerts).hasSize(alerted ? 1 : 0);
			if (alerted) {
				assertThat(alerts.getFirst().type()).isEqualTo(OperatorAlert.Type.EARLY_CHECKOUT);
				assertThat(alerts.getFirst().since()).isEqualTo(at(checkedOutAt));
			}
		}

		@Test
		void 오늘_체크인이_없는_방은_알리지_않는다() {
			assertThat(engine.alerts(context("09:40", List.of(room("1203", RoomStatus.CLEANING_PENDING)),
					departed(101, "1203", "09:40")))).isEmpty();
		}

		@Test
		void 청소를_시작하면_알림이_사라진다() {
			assertThat(engine.alerts(context("09:50", List.of(room("1203", RoomStatus.CLEANING)),
					arrival(1).in("1203"), departed(101, "1203", "09:40")))).isEmpty();
		}

	}

	@Nested
	class 다음_할_일 {

		@Test
		void 미배정이면_정오까지_객실_배정() {
			assertThat(nextStep("09:30", List.of(), arrival(1)))
				.isEqualTo(new NextStep(NextStep.Type.ASSIGN_ROOM, Actor.OPERATOR, at("12:00")));
		}

		@Test
		void 이전_투숙객이_있으면_예정_퇴실_시각까지_퇴실_대기() {
			ReservationSnapshot lateCheckout = new ArrivalBuilder(102).in("803").status(ReservationStatus.CHECKED_IN)
				.stay(DAY.minusDays(1), DAY, LocalTime.of(13, 0)).build();

			assertThat(nextStep("09:30", List.of(room("803", RoomStatus.OCCUPIED)), arrival(2).in("803"), lateCheckout))
				.isEqualTo(new NextStep(NextStep.Type.WAIT_CHECKOUT, Actor.GUEST, at("13:00")));
		}

		@Test
		void 청소_중이면_경고_시각까지_청소_완료() {
			assertThat(nextStep("11:00", List.of(room("1203", RoomStatus.CLEANING)), arrival(1).in("1203")))
				.isEqualTo(new NextStep(NextStep.Type.FINISH_CLEANING, Actor.OPERATOR, at("13:30")));
		}

		@Test
		void 준비된_방은_정오_안내_뒤_비밀번호_발송() {
			assertThat(nextStep("09:30", List.of(readyRoom("501", "09:00")), arrival(3).in("501")))
				.isEqualTo(new NextStep(NextStep.Type.NOON_NOTICE, Actor.SYSTEM, at("12:00")));
			assertThat(nextStep("12:30", List.of(readyRoom("501", "09:00")),
					arrival(3).in("501").noticed().offer(OfferStatus.DECLINED)))
				.isEqualTo(new NextStep(NextStep.Type.SEND_PASSWORD, Actor.SYSTEM, at("14:30")));
		}

	}

	@Nested
	class 얼리체크인_요금과_선택지 {

		@ParameterizedTest
		@CsvSource({ "14:00, 10000", "13:00, 20000", "13:20, 20000", "12:05, 30000", "12:00, 30000", "14:59, 10000" })
		void 기본_체크인까지_남은_시간을_올림해_시간당_요금을_곱한다(String checkInAt, int fee) {
			assertThat(engine.earlyCheckInFee(at(checkInAt))).isEqualTo(fee);
		}

		@Test
		void 지금_바로와_14시까지_남은_정각을_보여준다() {
			assertThat(engine.offerOptions(at("12:05"))).containsExactly(
					new EarlyCheckInOption(at("12:05"), true, 30_000),
					new EarlyCheckInOption(at("13:00"), false, 20_000),
					new EarlyCheckInOption(at("14:00"), false, 10_000));
		}

		@Test
		void 정각에_열면_그_정각은_지금_바로로만_보여준다() {
			assertThat(engine.offerOptions(at("13:00"))).extracting(EarlyCheckInOption::checkInAt)
				.containsExactly(at("13:00"), at("14:00"));
		}

		@Test
		void 마지막_얼리_입실_시각_정각까지만_선택지가_있다() {
			assertThat(engine.offerOptions(at("14:00"))).hasSize(1);
			assertThat(engine.offerOptions(at("14:01"))).isEmpty();
		}

	}

	@Nested
	class 결제_검증 {

		private final ReservationSnapshot offered = arrival(1).in("1203").noticed()
			.status(ReservationStatus.OFFER_SENT).offer(OfferStatus.SENT).build();

		@Test
		void 지금_바로와_남은_정각은_결제할_수_있다() {
			assertThat(engine.paymentRejection(at("12:05"), offered, at("12:05"))).isEmpty();
			assertThat(engine.paymentRejection(at("12:05"), offered, at("13:00"))).isEmpty();
		}

		@Test
		void 결제_화면을_연_채_고른_시각이_지나면_받지_않는다() {
			assertThat(engine.paymentRejection(at("13:05"), offered, at("13:00")))
				.contains(PaymentRejection.TIME_PASSED);
		}

		@Test
		void 선택지에_없는_시각은_받지_않는다() {
			assertThat(engine.paymentRejection(at("12:05"), offered, at("13:30")))
				.contains(PaymentRejection.INVALID_TIME);
			assertThat(engine.paymentRejection(at("12:05"), offered, at("15:00")))
				.contains(PaymentRejection.INVALID_TIME);
		}

		@Test
		void 마지막_얼리_입실_시각이_지나면_제안이_만료된다() {
			assertThat(engine.paymentRejection(at("14:01"), offered, at("14:01")))
				.contains(PaymentRejection.OFFER_EXPIRED);
		}

		@Test
		void 결제를_기다리는_제안이_없으면_받지_않는다() {
			assertThat(engine.paymentRejection(at("12:05"),
					arrival(1).in("1203").noticed().offer(OfferStatus.DECLINED).build(), at("13:00")))
				.contains(PaymentRejection.OFFER_NOT_OPEN);
		}

	}

	@Nested
	class 판단_근거 {

		@Test
		void 제안하지_않은_이유를_조건별로_보여준다() {
			// 14:10 준비 완료: 다른 조건은 맞지만 입실 가능 시각이 14:00을 넘었다
			RuleContext ctx = context("14:10", List.of(readyRoom("1203", "14:10")), arrival(1).in("1203").noticed());

			RuleCheck offer = engine.explain(ctx, "1203").getFirst();
			assertThat(offer.rule()).isEqualTo(RuleCheck.Rule.EARLY_OFFER);
			assertThat(offer.outcome()).isEqualTo(RuleCheck.Outcome.NOT_MET);
			assertThat(offer.conditions()).filteredOn(c -> !c.satisfied())
				.extracting(RuleCheck.Condition::type)
				.containsExactly(RuleCheck.Condition.Type.AVAILABLE_BY_DEADLINE);
		}

		@Test
		void 판단_근거와_실제_알림이_같다() {
			RuleContext ctx = context("09:40", List.of(room("1203", RoomStatus.CLEANING_PENDING)),
					arrival(1).in("1203"), departed(101, "1203", "09:40"));

			assertThat(engine.explain(ctx, "1203")).filteredOn(c -> c.rule() == RuleCheck.Rule.EARLY_CHECKOUT)
				.extracting(RuleCheck::outcome)
				.containsExactly(RuleCheck.Outcome.MET);
			assertThat(engine.alerts(ctx)).extracting(OperatorAlert::type)
				.containsExactly(OperatorAlert.Type.EARLY_CHECKOUT);
		}

		@Test
		void 발송된_제안과_비밀번호는_완료로_보여준다() {
			RuleContext ctx = context("13:05", List.of(readyRoom("1203", "11:10")), arrival(1).in("1203").noticed()
				.status(ReservationStatus.PASSWORD_SENT).offer(OfferStatus.ACCEPTED).earlyAt("13:00"));

			assertThat(engine.explain(ctx, "1203")).extracting(RuleCheck::rule, RuleCheck::outcome)
				.containsExactly(
						tuple(RuleCheck.Rule.EARLY_OFFER, RuleCheck.Outcome.DONE),
						tuple(RuleCheck.Rule.PASSWORD, RuleCheck.Outcome.DONE),
						tuple(RuleCheck.Rule.CLEANING_WARNING, RuleCheck.Outcome.NOT_MET));
		}

	}

	@Test
	void 시계_이동_사이의_규칙_시각과_얼리_입실_시각을_거쳐_간다() {
		RuleContext ctx = context("09:30", List.of(readyRoom("1203", "11:10")), arrival(1).in("1203").noticed()
			.status(ReservationStatus.EARLY_CONFIRMED).offer(OfferStatus.ACCEPTED).earlyAt("13:00"));

		assertThat(engine.checkpoints(ctx, at("09:30"), at("15:00")))
			.containsExactly(at("12:00"), at("13:00"), at("13:30"), at("14:30"));
	}

	private List<RuleAction> evaluate(String now, RoomSnapshot room, ArrivalBuilder arrival) {
		return engine.evaluate(context(now, List.of(room), arrival));
	}

	private NextStep nextStep(String now, List<RoomSnapshot> rooms, ArrivalBuilder arrival,
			ReservationSnapshot... others) {
		RuleContext ctx = context(now, rooms, arrival, others);
		return engine.nextStep(ctx, ctx.reservation(arrival.id).orElseThrow()).orElseThrow();
	}

	private RuleContext context(String now, List<RoomSnapshot> rooms, ArrivalBuilder arrival,
			ReservationSnapshot... others) {
		List<ReservationSnapshot> reservations = new ArrayList<>(List.of(others));
		reservations.add(arrival.build());
		return context(now, rooms, reservations);
	}

	private RuleContext context(String now, List<RoomSnapshot> rooms, ReservationSnapshot... reservations) {
		return context(now, rooms, List.of(reservations));
	}

	private RuleContext context(String now, List<RoomSnapshot> rooms, List<ReservationSnapshot> reservations) {
		RuleContext ctx = RuleContext.of(at(now), rooms, reservations);
		evaluated.add(ctx);
		return ctx;
	}

	private static LocalDateTime at(String time) {
		return DAY.atTime(LocalTime.parse(time));
	}

	private static RoomSnapshot room(String number, RoomStatus status) {
		return new RoomSnapshot(number, RoomType.STUDIO, 12, RoomView.CITY, status, null);
	}

	private static RoomSnapshot readyRoom(String number, String readyAt) {
		return new RoomSnapshot(number, RoomType.STUDIO, 12, RoomView.CITY, RoomStatus.READY, at(readyAt));
	}

	private static ArrivalBuilder arrival(long id) {
		return new ArrivalBuilder(id);
	}

	private static ReservationSnapshot departed(long id, String room, String checkedOutAt) {
		return new ArrivalBuilder(id).in(room).status(ReservationStatus.CHECKED_OUT)
			.stay(DAY.minusDays(1), DAY, LocalTime.of(11, 0)).checkedOut(checkedOutAt).build();
	}

	private static final class ArrivalBuilder {

		private final long id;

		private String roomNumber;

		private ReservationStatus status = ReservationStatus.PENDING_ASSIGNMENT;

		private LocalDate checkInDate = DAY;

		private LocalDate checkOutDate = DAY.plusDays(1);

		private LocalTime checkOutTime = LocalTime.of(11, 0);

		private LocalDateTime checkedOutAt;

		private LocalDateTime noonNoticeSentAt;

		private OfferStatus offerStatus;

		private LocalDateTime earlyCheckInAt;

		private LocalDateTime passwordHeldAt;

		private LocalDateTime delayNoticeSentAt;

		ArrivalBuilder(long id) {
			this.id = id;
		}

		ArrivalBuilder in(String roomNumber) {
			this.roomNumber = roomNumber;
			this.status = ReservationStatus.ASSIGNED;
			return this;
		}

		ArrivalBuilder status(ReservationStatus status) {
			this.status = status;
			return this;
		}

		ArrivalBuilder stay(LocalDate checkInDate, LocalDate checkOutDate, LocalTime checkOutTime) {
			this.checkInDate = checkInDate;
			this.checkOutDate = checkOutDate;
			this.checkOutTime = checkOutTime;
			return this;
		}

		ArrivalBuilder checkedOut(String time) {
			this.checkedOutAt = at(time);
			return this;
		}

		ArrivalBuilder noticed() {
			this.noonNoticeSentAt = at("12:00");
			return this;
		}

		ArrivalBuilder offer(OfferStatus offerStatus) {
			this.offerStatus = offerStatus;
			return this;
		}

		ArrivalBuilder earlyAt(String time) {
			this.earlyCheckInAt = at(time);
			return this;
		}

		ArrivalBuilder held(String time) {
			this.passwordHeldAt = at(time);
			return this;
		}

		ArrivalBuilder delayNoticed(String time) {
			this.delayNoticeSentAt = at(time);
			return this;
		}

		ReservationSnapshot build() {
			return new ReservationSnapshot(id, RoomType.STUDIO, List.of(), roomNumber, status, checkInDate,
					checkOutDate, checkOutTime, checkedOutAt, noonNoticeSentAt, offerStatus, earlyCheckInAt,
					passwordHeldAt, delayNoticeSentAt);
		}

	}

}
