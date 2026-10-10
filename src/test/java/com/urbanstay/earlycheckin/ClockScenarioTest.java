package com.urbanstay.earlycheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.urbanstay.earlycheckin.clock.ClockResponse;
import com.urbanstay.earlycheckin.clock.ClockResponse.Milestone;
import com.urbanstay.earlycheckin.dashboard.DashboardResponse;
import com.urbanstay.earlycheckin.dashboard.DashboardResponse.ArrivalRow;
import com.urbanstay.earlycheckin.domain.MessageLog;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.rule.NextStep;
import com.urbanstay.earlycheckin.rule.OperatorAlert;

/** 샘플 데이터에서 가상 시계만 움직였을 때 (배정·청소 이벤트 없이) 대시보드와 발송 기록을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ClockScenarioTest {

	private static final LocalDateTime START = LocalDateTime.of(2026, 10, 10, 9, 30);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private MessageLogRepository messageLogRepository;

	@BeforeEach
	void reset() {
		assertThat(mvc.post().uri("/api/scenario/reset")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.now")
			.isEqualTo("2026-10-10T09:30:00");
	}

	@Test
	void 시작_시각에는_배정_대기와_이미_준비된_방의_얼리체크인_제안이_보인다() {
		DashboardResponse dashboard = dashboard();

		assertThat(dashboard.now()).isEqualTo(START);
		assertThat(dashboard.summary().arrivals()).isEqualTo(4);
		assertThat(dashboard.summary().pendingAssignment()).isEqualTo(1);
		assertThat(dashboard.summary().awaitingCheckout()).isEqualTo(1);
		assertThat(dashboard.summary().ready()).isEqualTo(2);
		assertThat(dashboard.alerts()).isEmpty();
		assertThat(dashboard.arrivals()).extracting(ArrivalRow::guestName, row -> row.nextStep().type())
			.containsExactly(
					tuple("김민수", NextStep.Type.ASSIGN_ROOM),
					tuple("박준호", NextStep.Type.WAIT_CHECKOUT),
					tuple("이서연", NextStep.Type.GUEST_PAYMENT),
					tuple("최지훈", NextStep.Type.GUEST_PAYMENT));
		assertThat(dashboard.summary().earlyOffered()).isEqualTo(2);
	}

	@Test
	void 시계를_옮기면_제안_정오_안내_비밀번호_발송_보류가_원래_시각으로_기록된다() {
		advanceTo("15:20");

		// 501·901호는 시작 시각에 이미 준비되어 09:30에 제안을 받고, 결제하지 않았으므로 정오 안내도 받는다
		assertThat(sentMessages()).containsExactly(
				tuple(MessageType.EARLY_OFFER, "501", START),
				tuple(MessageType.EARLY_OFFER, "901", START),
				tuple(MessageType.NOON_NOTICE, "501", time("12:00")),
				tuple(MessageType.NOON_NOTICE, "803", time("12:00")),
				tuple(MessageType.NOON_NOTICE, "901", time("12:00")),
				tuple(MessageType.PASSWORD, "501", time("14:30")),
				tuple(MessageType.PASSWORD, "901", time("14:30")),
				tuple(MessageType.DELAY_NOTICE, "803", time("15:00")));

		DashboardResponse dashboard = dashboard();
		assertThat(dashboard.arrivals()).extracting(ArrivalRow::guestName, ArrivalRow::status)
			.containsExactly(
					tuple("김민수", ReservationStatus.PENDING_ASSIGNMENT),
					tuple("박준호", ReservationStatus.PASSWORD_HELD),
					tuple("이서연", ReservationStatus.PASSWORD_SENT),
					tuple("최지훈", ReservationStatus.PASSWORD_SENT));
		assertThat(dashboard.alerts()).extracting(DashboardResponse.AlertItem::type, DashboardResponse.AlertItem::level)
			.containsExactly(
					tuple(OperatorAlert.Type.PASSWORD_HELD, OperatorAlert.Level.CRITICAL),
					tuple(OperatorAlert.Type.CLEANING_DELAY, OperatorAlert.Level.CRITICAL),
					tuple(OperatorAlert.Type.UNASSIGNED, OperatorAlert.Level.WARNING));
		assertThat(dashboard.summary().earlyOffered()).isEqualTo(2);
		assertThat(dashboard.summary().passwordsToUnreadyRooms()).isZero();
	}

	@Test
	void 한_번에_건너뛰든_10분씩_옮기든_결과가_같다() {
		advanceTo("15:20");
		List<Tuple> jumped = sentMessages();

		reset();
		for (LocalDateTime t = START.plusMinutes(10); !t.isAfter(time("15:20")); t = t.plusMinutes(10)) {
			advanceTo(t.toLocalTime().toString());
		}

		assertThat(sentMessages()).isEqualTo(jumped);
	}

	@Test
	void 시계는_타임라인에_표시할_오늘의_규칙_시각을_함께_알려준다() {
		ClockResponse clock = mvc.get().uri("/api/clock").exchange().assertThat().hasStatusOk()
			.bodyJson().convertTo(ClockResponse.class).actual();

		assertThat(clock.now()).isEqualTo(START);
		assertThat(clock.milestones()).extracting(Milestone::at, Milestone::type)
			.containsExactly(
					tuple(time("09:30"), Milestone.Type.OFFER_START),
					tuple(time("12:00"), Milestone.Type.NOON_NOTICE),
					tuple(time("13:30"), Milestone.Type.CLEANING_WARNING),
					tuple(time("14:00"), Milestone.Type.OFFER_EXPIRY),
					tuple(time("14:30"), Milestone.Type.PASSWORD_SEND),
					tuple(time("15:00"), Milestone.Type.STANDARD_CHECK_IN));
	}

	@Test
	void 시계를_되돌릴_수는_없다() {
		assertThat(mvc.post().uri("/api/clock/advance").contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\": \"2026-10-10T09:00:00\"}"))
			.hasStatus4xxClientError();
	}

	private void advanceTo(String time) {
		assertThat(mvc.post().uri("/api/clock/advance").contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\": \"" + time(time) + "\"}"))
			.hasStatusOk();
	}

	private DashboardResponse dashboard() {
		return mvc.get().uri("/api/dashboard").exchange().assertThat().hasStatusOk()
			.bodyJson()
			.convertTo(DashboardResponse.class)
			.actual();
	}

	private List<Tuple> sentMessages() {
		return messageLogRepository.findAll().stream()
			.sorted(Comparator.comparing(MessageLog::getSentAt).thenComparing(MessageLog::getRoomNumber))
			.map(m -> tuple(m.getType(), m.getRoomNumber(), m.getSentAt()))
			.toList();
	}

	private static LocalDateTime time(String time) {
		return START.toLocalDate().atTime(LocalTime.parse(time));
	}

}
