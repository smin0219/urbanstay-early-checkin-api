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

import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomEvent;
import com.urbanstay.earlycheckin.domain.RoomStatus;
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

	private RoomDetailResponse room(String number) {
		return api.get("/api/rooms/" + number, RoomDetailResponse.class);
	}

}
