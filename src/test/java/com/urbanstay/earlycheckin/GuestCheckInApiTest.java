package com.urbanstay.earlycheckin;

import static com.urbanstay.earlycheckin.ScenarioApi.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.urbanstay.earlycheckin.dashboard.DashboardResponse;
import com.urbanstay.earlycheckin.domain.MessageLog;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.Stage;
import com.urbanstay.earlycheckin.rule.EarlyCheckInOption;

/** 이서연 예약(id 3, 501호 전날 공실)으로 고객 화면 API의 경계 상황을 확인한다. 501호는 시작 시각(09:30)에 제안을 받는다. */
@SpringBootTest
@AutoConfigureMockMvc
class GuestCheckInApiTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private MessageLogRepository messageLogRepository;

	private ScenarioApi api;

	private String token;

	@BeforeEach
	void setUp() {
		api = new ScenarioApi(mvc);
		api.reset();
		token = api.checkInToken(3);
	}

	@Test
	void 정오_전에_받은_제안에는_호수와_비밀번호를_보여주지_않는다() {
		GuestCheckInResponse view = view();

		assertThat(view.stage()).isEqualTo(Stage.OFFER_OPEN);
		assertThat(view.room()).isNull();
		assertThat(view.password()).isNull();
		assertThat(view.offer().options())
			.extracting(EarlyCheckInOption::checkInAt, EarlyCheckInOption::immediate, EarlyCheckInOption::fee)
			.containsExactly(tuple(at("09:30"), true, 40_000), tuple(at("12:00"), false, 30_000),
					tuple(at("13:00"), false, 20_000), tuple(at("14:00"), false, 10_000));
	}

	@Test
	void 정오_전에_지금_바로_결제하면_호수와_비밀번호를_바로_받고_정오_안내는_보내지_않는다() {
		api.advanceTo("10:00");

		GuestCheckInResponse view = api.read(api.post(paymentUri(), payment(null, 40_000, "now")),
				GuestCheckInResponse.class);

		assertThat(view.stage()).isEqualTo(Stage.PASSWORD_READY);
		assertThat(view.room().number()).isEqualTo("501");
		assertThat(view.earlyCheckIn().checkInAt()).isEqualTo(at("10:00"));
		api.advanceTo("12:10");
		assertThat(messageLogRepository.findByRoomNumberOrderBySentAtAscIdAsc("501"))
			.extracting(MessageLog::getType, MessageLog::getSentAt)
			.containsExactly(
					tuple(MessageType.EARLY_OFFER, at("09:30")),
					tuple(MessageType.EARLY_CONFIRMATION, at("10:00")),
					tuple(MessageType.PASSWORD, at("10:00")));
	}

	@Test
	void 같은_멱등_키로_다시_결제하면_한_번만_결제된다() {
		api.advanceTo("12:05");
		String body = payment(at("13:00"), 20_000, "same-key");

		assertThat(api.post(paymentUri(), body)).hasStatusOk();
		assertThat(api.post(paymentUri(), body)).hasStatusOk();
		assertThat(api.post(paymentUri(), payment(at("14:00"), 10_000, "other-key"))).hasStatus(HttpStatus.CONFLICT);

		assertThat(view().earlyCheckIn().fee()).isEqualTo(20_000);
		assertThat(api.get("/api/dashboard", DashboardResponse.class).summary().earlyRevenue()).isEqualTo(20_000);
	}

	@Test
	void 화면에_보인_요금과_다르면_결제하지_않는다() {
		api.advanceTo("12:05");

		assertThat(api.post(paymentUri(), payment(at("13:00"), 30_000, "wrong-fee"))).hasStatus(HttpStatus.CONFLICT);
		assertThat(view().stage()).isEqualTo(Stage.OFFER_OPEN);
	}

	@Test
	void 고른_정각이_되면_결제하지_않는다() {
		api.advanceTo("13:00");

		assertThat(api.post(paymentUri(), payment(at("13:00"), 20_000, "late"))).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void 마지막_얼리_입실_정각이_되면_결제를_막고_비밀번호_발송_시각을_안내한다() {
		api.advanceTo("14:00");

		MvcTestResult result = api.post(paymentUri(), payment(at("14:00"), 10_000, "expired"));

		assertThat(result).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.detail")
			.asString()
			.contains("14:30");
		assertThat(view().stage()).isEqualTo(Stage.WAITING_PASSWORD);
		assertThat(view().offerStatus()).isEqualTo(OfferStatus.EXPIRED);
	}

	@Test
	void 거절하면_비밀번호_발송_시각을_기다린다() {
		api.advanceTo("12:10");

		GuestCheckInResponse view = api.read(api.post(base() + "/decline", null), GuestCheckInResponse.class);

		assertThat(view.stage()).isEqualTo(Stage.WAITING_PASSWORD);
		assertThat(view.passwordAt()).isEqualTo(at("14:30"));
	}

	@Test
	void 비밀번호를_받기_전에는_입실할_수_없다() {
		api.advanceTo("12:10");

		assertThat(api.post(base() + "/door", null)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void 잘못된_링크는_404() {
		assertThat(mvc.get().uri("/api/check-in/not-a-token")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void 퇴실_시각이_지난_투숙객_링크는_만료된다() {
		// 윤서준(id 101)은 오늘 11:00 퇴실 예정
		String departed = api.checkInToken(101);
		api.advanceTo("11:00");

		assertThat(mvc.get().uri("/api/check-in/" + departed)).hasStatus(HttpStatus.NOT_FOUND);
	}

	private GuestCheckInResponse view() {
		return api.get(base(), GuestCheckInResponse.class);
	}

	private String base() {
		return "/api/check-in/" + token;
	}

	private String paymentUri() {
		return base() + "/payment";
	}

	/** checkInAt이 null이면 "지금 바로" */
	private static String payment(LocalDateTime checkInAt, int fee, String key) {
		String time = checkInAt == null ? "null" : "\"" + checkInAt + "\"";
		return "{\"checkInAt\": " + time + ", \"expectedFee\": " + fee + ", \"idempotencyKey\": \"" + key + "\"}";
	}

}
