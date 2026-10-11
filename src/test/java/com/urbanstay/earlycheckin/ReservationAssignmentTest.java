package com.urbanstay.earlycheckin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.CandidateItem;
import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse.ExcludedItem;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Reason;
import com.urbanstay.earlycheckin.rule.RoomAssignmentRule;

/**
 * 샘플 데이터의 김민수 예약(id 1, Studio, 고층·시티뷰 요청)으로 배정 화면 API를 확인하고, 데모 시나리오 밖에서 직접 배정해 보도록
 * 둔 미배정 예약 3건(정유진·오세훈·임지아)의 추천 결과를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationAssignmentTest {

	private static final long KIM_MINSU = 1;

	private static final long JUNG_YUJIN = 5;

	private static final long OH_SEHUN = 6;

	private static final long LIM_JIA = 7;

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private MessageLogRepository messageLogRepository;

	@BeforeEach
	void reset() {
		assertThat(mvc.post().uri("/api/scenario/reset")).hasStatusOk();
	}

	@Test
	void 요청_2개_일치한_1203호가_1순위_시티뷰_불일치_802호가_2순위다() {
		ReservationDetailResponse detail = detail(KIM_MINSU);

		assertThat(detail.assignment().lock()).isNull();
		assertThat(detail.assignment().candidates())
			.extracting(c -> c.room().number(), CandidateItem::matchCount, CandidateItem::vacantLastNight)
			.containsExactly(tuple("1203", 2L, false), tuple("802", 1L, true));
		assertThat(detail.assignment().excluded())
			.extracting(e -> e.room().number(), e -> e.reasons().getFirst().reason())
			.containsExactly(
					tuple("803", Reason.STAY_OVERLAP),
					tuple("1001", Reason.OUT_OF_SERVICE),
					tuple("1105", Reason.STAY_OVERLAP),
					tuple("501", Reason.ROOM_TYPE_MISMATCH),
					tuple("601", Reason.ROOM_TYPE_MISMATCH),
					tuple("701", Reason.ROOM_TYPE_MISMATCH),
					tuple("702", Reason.ROOM_TYPE_MISMATCH),
					tuple("901", Reason.ROOM_TYPE_MISMATCH),
					tuple("902", Reason.ROOM_TYPE_MISMATCH));
	}

	@Test
	void 시티뷰를_요청한_정유진은_701호가_1순위_702호가_2순위이고_501호는_숙박이_겹친다() {
		ReservationDetailResponse detail = detail(JUNG_YUJIN);

		assertThat(detail.assignment().candidates())
			.extracting(c -> c.room().number(), CandidateItem::matchCount)
			.containsExactly(tuple("701", 1L), tuple("702", 0L));
		assertThat(detail.assignment().excluded())
			.filteredOn(e -> e.room().number().equals("501"))
			.extracting(e -> e.reasons().getFirst().reason(), e -> e.reasons().getFirst().conflict().guestName())
			.containsExactly(tuple(Reason.STAY_OVERLAP, "이서연"));
	}

	@Test
	void 연박하는_오세훈은_내일_예약이_잡힌_601호를_제외하고_902호만_추천한다() {
		ReservationDetailResponse detail = detail(OH_SEHUN);

		assertThat(detail.assignment().candidates()).extracting(c -> c.room().number()).containsExactly("902");
		assertThat(detail.assignment().excluded())
			.filteredOn(e -> e.reasons().getFirst().reason() == Reason.STAY_OVERLAP)
			.extracting(e -> e.room().number(), e -> e.reasons().getFirst().conflict().guestName())
			.containsExactlyInAnyOrder(tuple("601", "배수아"), tuple("901", "최지훈"));
	}

	@Test
	void 요청이_없는_임지아는_김민수가_1203호를_가져가면_802호만_남는다() {
		assertThat(detail(LIM_JIA).assignment().candidates()).extracting(c -> c.room().number())
			.containsExactly("802", "1203");

		assertThat(assign(KIM_MINSU, "1203")).hasStatusOk();

		ReservationDetailResponse detail = detail(LIM_JIA);
		assertThat(detail.assignment().candidates()).extracting(c -> c.room().number()).containsExactly("802");
		assertThat(detail.assignment().excluded())
			.filteredOn(e -> e.room().number().equals("1203"))
			.extracting(e -> e.reasons().getFirst().conflict().guestName())
			.containsExactly("김민수");
	}

	@Test
	void 연박_중인_방은_겹치는_예약을_제외_이유로_보여준다() {
		ExcludedItem longStay = detail(KIM_MINSU).assignment()
			.excluded()
			.stream()
			.filter(e -> e.room().number().equals("1105"))
			.findFirst()
			.orElseThrow();

		assertThat(longStay.reasons().getFirst().conflict().guestName()).isEqualTo("강도윤");
	}

	@Test
	void 추천_객실로_배정하면_배정됨이_되고_정오_전에는_다시_바꿀_수_있다() {
		ReservationDetailResponse assigned = assign(KIM_MINSU, "1203").assertThat()
			.hasStatusOk()
			.bodyJson()
			.convertTo(ReservationDetailResponse.class)
			.actual();

		assertThat(assigned.reservation().status()).isEqualTo(ReservationStatus.ASSIGNED);
		assertThat(assigned.reservation().room().number()).isEqualTo("1203");
		assertThat(assigned.reservation().assignedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 9, 30));
		assertThat(assigned.assignment().candidates()).filteredOn(CandidateItem::current)
			.extracting(c -> c.room().number())
			.containsExactly("1203");

		assertThat(assign(KIM_MINSU, "802")).hasStatusOk();
		assertThat(detail(KIM_MINSU).reservation().room().number()).isEqualTo("802");
	}

	@Test
	void 정오_안내_전에는_배정을_취소해_배정_대기로_되돌릴_수_있다() {
		assertThat(assign(KIM_MINSU, "1203")).hasStatusOk();

		ReservationDetailResponse unassigned = mvc.delete().uri("/api/reservations/{id}/assignment", KIM_MINSU)
			.exchange().assertThat().hasStatusOk().bodyJson().convertTo(ReservationDetailResponse.class).actual();

		assertThat(unassigned.reservation().status()).isEqualTo(ReservationStatus.PENDING_ASSIGNMENT);
		assertThat(unassigned.reservation().room()).isNull();
		assertThat(unassigned.reservation().assignedAt()).isNull();
		assertThat(unassigned.assignment().candidates()).noneMatch(CandidateItem::current);
	}

	@Test
	void 배정되지_않은_예약은_취소할_수_없다() {
		assertThat(mvc.delete().uri("/api/reservations/{id}/assignment", KIM_MINSU)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void 후보가_아닌_객실로는_배정할_수_없다() {
		assertThat(assign(KIM_MINSU, "1105")).hasStatus(HttpStatus.CONFLICT);
		assertThat(detail(KIM_MINSU).reservation().room()).isNull();
	}

	@Test
	void 정오가_지나_배정하면_정오_안내가_바로_나가고_이후에는_바꿀_수_없다() {
		assertThat(mvc.post().uri("/api/clock/advance").contentType(MediaType.APPLICATION_JSON)
			.content("{\"to\": \"2026-10-10T12:10:00\"}")).hasStatusOk();

		assertThat(assign(KIM_MINSU, "1203")).hasStatusOk();

		assertThat(messageLogRepository.findAll())
			.filteredOn(m -> m.getRoomNumber().equals("1203"))
			.extracting(m -> m.getType(), m -> m.getSentAt())
			.containsExactly(tuple(MessageType.NOON_NOTICE, LocalDateTime.of(2026, 10, 10, 12, 10)));
		assertThat(detail(KIM_MINSU).assignment().lock()).isEqualTo(RoomAssignmentRule.Lock.NOTICE_SENT);
		assertThat(assign(KIM_MINSU, "802")).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.delete().uri("/api/reservations/{id}/assignment", KIM_MINSU)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void 투숙_중인_예약은_배정이_잠겨_있다() {
		assertThat(detail(103).assignment().lock()).isEqualTo(RoomAssignmentRule.Lock.CHECKED_IN);
	}

	@Test
	void 없는_예약은_404() {
		assertThat(mvc.get().uri("/api/reservations/999")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private ReservationDetailResponse detail(long id) {
		return mvc.get().uri("/api/reservations/{id}", id).exchange().assertThat()
			.hasStatusOk()
			.bodyJson()
			.convertTo(ReservationDetailResponse.class)
			.actual();
	}

	private MvcTestResult assign(long id, String roomNumber) {
		return mvc.post().uri("/api/reservations/{id}/assignment", id)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"roomNumber\": \"" + roomNumber + "\"}")
			.exchange();
	}

}
