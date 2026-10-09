package com.urbanstay.earlycheckin;

import static com.urbanstay.earlycheckin.ScenarioApi.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.urbanstay.earlycheckin.dashboard.DashboardResponse;
import com.urbanstay.earlycheckin.dashboard.DashboardResponse.AlertItem;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.Stage;
import com.urbanstay.earlycheckin.rule.EarlyCheckInOption;
import com.urbanstay.earlycheckin.rule.OperatorAlert;

/**
 * 기획서 "데모 시나리오" 1~9를 API로 처음부터 끝까지 재생한다. 성공 경로(1203호 김민수)와 예외 경로(803호 박준호)를 함께
 * 확인하고, 마감 지표(미준비 객실 비밀번호 0건, 얼리 확정 1건, 매출 20,000원)를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoScenarioTest {

	private static final long KIM_MINSU = 1;

	private static final long PARK_JUNHO = 2;

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private MessageLogRepository messageLogRepository;

	@Test
	void 데모_시나리오_1부터_9까지() {
		ScenarioApi api = new ScenarioApi(mvc);
		api.reset();

		// 1. 09:30 김민수 예약이 배정 대기
		assertThat(dashboard(api).arrivals().getFirst().status()).isEqualTo(ReservationStatus.PENDING_ASSIGNMENT);

		// 2. 09:35 1203호 배정
		assertThat(api.postAt("09:35", "/api/reservations/1/assignment", "{\"roomNumber\": \"1203\"}")).hasStatusOk();

		// 3. 09:40 1203호 이전 투숙객 조기 퇴실 → 조기 퇴실 알림
		assertThat(api.postAt("09:40", "/api/rooms/1203/checkout", null)).hasStatusOk();
		assertThat(dashboard(api).alerts()).extracting(AlertItem::type, AlertItem::roomNumber)
			.containsExactly(tuple(OperatorAlert.Type.EARLY_CHECKOUT, "1203"));

		// 4. 09:50 청소 시작으로 알림 해제, 11:10 청소 완료
		assertThat(api.postAt("09:50", "/api/rooms/1203/cleaning/start", null)).hasStatusOk();
		assertThat(dashboard(api).alerts()).isEmpty();
		assertThat(api.postAt("11:10", "/api/rooms/1203/cleaning/finish", null)).hasStatusOk();

		// 5. 12:00 정오 안내: 김민수는 얼리체크인 제안 포함, 박준호는 안내만
		api.advanceTo("12:00");
		String token = api.checkInToken(KIM_MINSU);
		GuestCheckInResponse offered = api.get("/api/check-in/" + token, GuestCheckInResponse.class);
		assertThat(offered.stage()).isEqualTo(Stage.OFFER_OPEN);
		assertThat(offered.offer().options()).extracting(EarlyCheckInOption::checkInAt, EarlyCheckInOption::fee)
			.containsExactly(tuple(at("12:00"), 30_000), tuple(at("13:00"), 20_000), tuple(at("14:00"), 10_000));
		assertThat(api.get("/api/check-in/" + api.checkInToken(PARK_JUNHO), GuestCheckInResponse.class).stage())
			.isEqualTo(Stage.WAITING_PASSWORD);

		// 6. 12:05 13:00 입실을 20,000원에 결제 → 13:00 비밀번호 → 13:10 입실
		GuestCheckInResponse paid = api.read(api.postAt("12:05", "/api/check-in/" + token + "/payment",
				"{\"checkInAt\": \"" + at("13:00") + "\", \"expectedFee\": 20000, \"idempotencyKey\": \"pay-1\"}"),
				GuestCheckInResponse.class);
		assertThat(paid.stage()).isEqualTo(Stage.EARLY_CONFIRMED);
		api.advanceTo("13:00");
		GuestCheckInResponse password = api.get("/api/check-in/" + token, GuestCheckInResponse.class);
		assertThat(password.stage()).isEqualTo(Stage.PASSWORD_READY);
		assertThat(password.password().doorCode()).hasSize(6);
		assertThat(api.postAt("13:10", "/api/check-in/" + token + "/door", null)).hasStatusOk();
		assertThat(dashboard(api).summary().checkedIn()).isEqualTo(1);

		// 7. 803호 레이트 체크아웃(예정 13:00) 후 청소 중 → 13:30 경고
		assertThat(api.postAt("13:20", "/api/rooms/803/checkout", null)).hasStatusOk();
		assertThat(api.postAt("13:25", "/api/rooms/803/cleaning/start", null)).hasStatusOk();
		api.advanceTo("13:30");
		assertThat(dashboard(api).alerts()).extracting(AlertItem::type, AlertItem::roomNumber)
			.contains(tuple(OperatorAlert.Type.CLEANING_DELAY, "803"));

		// 8. 14:30 803호 비밀번호 보류 → 15:00 이후 지연 안내 → 15:20 청소 완료 즉시 비밀번호
		api.advanceTo("14:30");
		assertThat(dashboard(api).alerts()).extracting(AlertItem::type)
			.contains(OperatorAlert.Type.PASSWORD_HELD);
		api.advanceTo("15:10");
		assertThat(api.postAt("15:20", "/api/rooms/803/cleaning/finish", null)).hasStatusOk();

		assertThat(messages("1203")).containsExactly(
				tuple(MessageType.NOON_NOTICE, at("12:00")),
				tuple(MessageType.PASSWORD, at("13:00")));
		assertThat(messages("803")).containsExactly(
				tuple(MessageType.NOON_NOTICE, at("12:00")),
				tuple(MessageType.DELAY_NOTICE, at("15:00")),
				tuple(MessageType.PASSWORD, at("15:20")));

		// 9. 마감 요약
		DashboardResponse.Summary summary = dashboard(api).summary();
		assertThat(summary.passwordsToUnreadyRooms()).isZero();
		assertThat(summary.earlyConfirmed()).isEqualTo(1);
		assertThat(summary.earlyRevenue()).isEqualTo(20_000);
		assertThat(messageLogRepository.findAll()).filteredOn(m -> m.getType() == MessageType.PASSWORD)
			.allSatisfy(m -> assertThat(m.getRoomStatus()).isEqualTo(RoomStatus.READY));
	}

	private DashboardResponse dashboard(ScenarioApi api) {
		return api.get("/api/dashboard", DashboardResponse.class);
	}

	private List<Tuple> messages(String roomNumber) {
		return messageLogRepository.findByRoomNumberOrderBySentAtAscIdAsc(roomNumber)
			.stream()
			.map(m -> tuple(m.getType(), m.getSentAt()))
			.toList();
	}

}
