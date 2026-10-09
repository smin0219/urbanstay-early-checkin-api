package com.urbanstay.earlycheckin;

import static com.urbanstay.earlycheckin.ScenarioApi.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.urbanstay.earlycheckin.dashboard.DashboardResponse;
import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomEvent;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse;
import com.urbanstay.earlycheckin.room.RoomDetailResponse;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.Action;
import com.urbanstay.earlycheckin.room.RoomDetailResponse.TimelineEntry;
import com.urbanstay.earlycheckin.rule.RuleCheck;

@SpringBootTest
@AutoConfigureMockMvc
class RoomEventApiTest {

	@Autowired
	private MockMvcTester mvc;

	private ScenarioApi api;

	@BeforeEach
	void setUp() {
		api = new ScenarioApi(mvc);
		api.reset();
	}

	@Test
	void 퇴실_청소_시작_청소_완료가_타임라인과_버튼에_반영된다() {
		assertThat(room("803").actions()).containsExactly(Action.CHECK_OUT);

		api.postAt("13:20", "/api/rooms/803/checkout", null);
		assertThat(room("803").actions()).containsExactly(Action.START_CLEANING);
		api.postAt("13:25", "/api/rooms/803/cleaning/start", null);
		RoomDetailResponse finished = api.read(api.postAt("13:50", "/api/rooms/803/cleaning/finish", null),
				RoomDetailResponse.class);

		assertThat(finished.room().status()).isEqualTo(RoomStatus.READY);
		assertThat(finished.room().readyAt()).isEqualTo(at("13:50"));
		assertThat(finished.departure().status()).isEqualTo(ReservationStatus.CHECKED_OUT);
		assertThat(finished.timeline()).extracting(TimelineEntry::event, TimelineEntry::to, TimelineEntry::at)
			.containsExactly(
					tuple(RoomEvent.INITIAL, RoomStatus.OCCUPIED, at("09:30")),
					tuple(RoomEvent.CHECK_OUT, RoomStatus.CLEANING_PENDING, at("13:20")),
					tuple(RoomEvent.CLEANING_STARTED, RoomStatus.CLEANING, at("13:25")),
					tuple(RoomEvent.CLEANING_FINISHED, RoomStatus.READY, at("13:50")));
	}

	@Test
	void 순서가_맞지_않는_객실_이벤트는_409() {
		assertThat(api.post("/api/rooms/803/cleaning/finish", null)).hasStatus(HttpStatus.CONFLICT);
		// 1105호는 연박 중이라 오늘 퇴실할 투숙객이 없다
		assertThat(api.post("/api/rooms/1105/checkout", null)).hasStatus(HttpStatus.CONFLICT);
		assertThat(api.post("/api/rooms/9999/checkout", null)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void 규칙_판단_근거를_조건별로_보여준다() {
		// 박준호(803호) 12:00: 객실 미준비라 제안 조건 미충족
		api.advanceTo("12:00");

		RuleCheck offer = room("803").ruleChecks().getFirst();

		assertThat(offer.rule()).isEqualTo(RuleCheck.Rule.EARLY_OFFER);
		assertThat(offer.outcome()).isEqualTo(RuleCheck.Outcome.NOT_MET);
		assertThat(offer.conditions()).filteredOn(c -> !c.satisfied())
			.extracting(RuleCheck.Condition::type)
			.containsExactly(RuleCheck.Condition.Type.ROOM_READY, RuleCheck.Condition.Type.AVAILABLE_BY_DEADLINE);
	}

	@Test
	void 결제_후_객실_문제를_발견하면_얼리체크인을_취소하고_환불한다() {
		// 이서연(501호)이 12:05에 13:00 입실을 결제한 뒤 12:30에 문제 발견
		String token = api.checkInToken(3);
		api.advanceTo("12:05");
		assertThat(api.post("/api/check-in/" + token + "/payment",
				"{\"checkInAt\": \"" + at("13:00") + "\", \"expectedFee\": 20000, \"idempotencyKey\": \"k\"}"))
			.hasStatusOk();

		RoomDetailResponse room = api.read(api.postAt("12:30", "/api/rooms/501/issue", null), RoomDetailResponse.class);

		assertThat(room.room().status()).isEqualTo(RoomStatus.CLEANING);
		GuestCheckInResponse guest = api.get("/api/check-in/" + token, GuestCheckInResponse.class);
		assertThat(guest.offerStatus()).isEqualTo(OfferStatus.REFUNDED);
		assertThat(guest.stage()).isEqualTo(GuestCheckInResponse.Stage.WAITING_PASSWORD);
		DashboardResponse.Summary summary = api.get("/api/dashboard", DashboardResponse.class).summary();
		assertThat(summary.earlyConfirmed()).isZero();
		assertThat(summary.earlyRevenue()).isZero();

		// 13:00이 되어도 미준비라 비밀번호를 보내지 않는다
		api.advanceTo("13:00");
		assertThat(api.get("/api/check-in/" + token, GuestCheckInResponse.class).password()).isNull();
	}

	private RoomDetailResponse room(String number) {
		return api.get("/api/rooms/" + number, RoomDetailResponse.class);
	}

}
