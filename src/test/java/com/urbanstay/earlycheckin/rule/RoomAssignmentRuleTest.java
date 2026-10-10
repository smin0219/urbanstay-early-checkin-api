package com.urbanstay.earlycheckin.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.domain.RoomRequest;
import com.urbanstay.earlycheckin.domain.RoomStatus;
import com.urbanstay.earlycheckin.domain.RoomType;
import com.urbanstay.earlycheckin.domain.RoomView;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Candidate;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Excluded;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Exclusion;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.Reason;
import com.urbanstay.earlycheckin.rule.AssignmentOptions.RequestMatch;

class RoomAssignmentRuleTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

	private static final CheckInPolicy POLICY = new CheckInPolicy(LocalTime.of(12, 0), LocalTime.of(14, 0),
			LocalTime.of(13, 30), LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(11, 0),
			Duration.ofMinutes(30), 8, 10_000, 4, LocalTime.of(9, 30), LocalTime.of(12, 0), Duration.ofMinutes(15));

	private final RoomAssignmentRule rule = new RoomAssignmentRule(POLICY);

	/** 오늘 체크인 1박, Studio, 고층·시티뷰 요청 */
	private final ReservationSnapshot target = reservation(1, RoomType.STUDIO, null, DAY, DAY.plusDays(1),
			ReservationStatus.PENDING_ASSIGNMENT, RoomRequest.HIGH_FLOOR, RoomRequest.CITY_VIEW);

	@Nested
	class 후보_거르기 {

		@Test
		void 체크아웃_당일의_다음_체크인은_겹치지_않는다() {
			AssignmentOptions options = rule.options(target, List.of(room("1203", 12, RoomView.CITY)),
					List.of(stay(101, "1203", DAY.minusDays(2), DAY)));

			assertThat(options.candidates()).extracting(Candidate::roomNumber).containsExactly("1203");
			assertThat(options.excluded()).isEmpty();
		}

		@Test
		void 연박_중인_방은_겹치는_예약과_함께_제외한다() {
			AssignmentOptions options = rule.options(target, List.of(room("1105", 11, RoomView.CITY)),
					List.of(stay(103, "1105", DAY.minusDays(2), DAY.plusDays(2))));

			assertThat(options.candidates()).isEmpty();
			assertThat(options.excluded()).singleElement()
				.extracting(Excluded::reasons)
				.isEqualTo(List.of(new Exclusion(Reason.STAY_OVERLAP, 103L)));
		}

		@Test
		void 이미_다른_예약이_배정된_방은_제외한다() {
			assertThat(rule.options(target, List.of(room("803", 8, RoomView.CITY)),
					List.of(reservation(2, RoomType.STUDIO, "803", DAY, DAY.plusDays(2), ReservationStatus.ASSIGNED)))
				.candidates()).isEmpty();
		}

		@Test
		void 자기_자신의_현재_배정은_겹침으로_보지_않는다() {
			ReservationSnapshot assigned = reservation(1, RoomType.STUDIO, "1203", DAY, DAY.plusDays(1),
					ReservationStatus.ASSIGNED);

			assertThat(rule.options(assigned, List.of(room("1203", 12, RoomView.CITY)), List.of(assigned))
				.candidates()).extracting(Candidate::roomNumber).containsExactly("1203");
		}

		@Test
		void 타입이_다르거나_운영_중지인_방은_제외하고_다른_타입은_뒤에_둔다() {
			RoomSnapshot twin = new RoomSnapshot("501", RoomType.TWIN, 5, RoomView.COURTYARD, RoomStatus.READY, null);
			RoomSnapshot outOfService = new RoomSnapshot("1001", RoomType.STUDIO, 10, RoomView.CITY,
					RoomStatus.OUT_OF_SERVICE, null);

			assertThat(rule.options(target, List.of(twin, outOfService), List.of()).excluded())
				.extracting(Excluded::roomNumber, e -> e.reasons().getFirst().reason())
				.containsExactly(tuple("1001", Reason.OUT_OF_SERVICE), tuple("501", Reason.ROOM_TYPE_MISMATCH));
		}

	}

	@Nested
	class 요청사항_비교 {

		@Test
		void 고층은_기준_층부터_일치한다() {
			AssignmentOptions options = rule.options(target,
					List.of(room("802", 8, RoomView.COURTYARD), room("702", 7, RoomView.CITY)), List.of());

			assertThat(options.candidates()).extracting(Candidate::roomNumber, Candidate::matches)
				.containsExactly(
						tuple("702", List.of(new RequestMatch(RoomRequest.HIGH_FLOOR, false),
								new RequestMatch(RoomRequest.CITY_VIEW, true))),
						tuple("802", List.of(new RequestMatch(RoomRequest.HIGH_FLOOR, true),
								new RequestMatch(RoomRequest.CITY_VIEW, false))));
		}

	}

	@Test
	void 요청_일치_수_전날_공실_객실_번호_순으로_추천한다() {
		List<RoomSnapshot> rooms = List.of(
				room("702", 7, RoomView.COURTYARD),
				room("1202", 12, RoomView.COURTYARD),
				room("902", 9, RoomView.COURTYARD),
				room("802", 8, RoomView.COURTYARD),
				room("1203", 12, RoomView.CITY));
		// 802만 전날 밤 투숙객이 있었다
		List<ReservationSnapshot> reservations = List.of(stay(104, "802", DAY.minusDays(1), DAY));

		assertThat(rule.options(target, rooms, reservations).candidates())
			.extracting(Candidate::roomNumber, Candidate::matchCount, Candidate::vacantLastNight)
			.containsExactly(
					tuple("1203", 2L, true),
					tuple("902", 1L, true),
					tuple("1202", 1L, true),
					tuple("802", 1L, false),
					tuple("702", 0L, true));
	}

	@Nested
	class 배정_변경_잠금 {

		@Test
		void 정오_안내_전에는_다시_배정할_수_있다() {
			assertThat(rule.lock(reservation(1, RoomType.STUDIO, "1203", DAY, DAY.plusDays(1),
					ReservationStatus.ASSIGNED))).isEmpty();
		}

		@Test
		void 정오_안내나_제안이_나가면_바꿀_수_없다() {
			ReservationSnapshot offered = new ReservationSnapshot(1, RoomType.STUDIO, List.of(), "1203",
					ReservationStatus.OFFER_SENT, DAY, DAY.plusDays(1), LocalTime.of(11, 0), null,
					null, OfferStatus.SENT, DAY.atTime(11, 10), null, null, null, null);

			assertThat(rule.lock(offered)).contains(RoomAssignmentRule.Lock.NOTICE_SENT);
		}

		@Test
		void 입실한_예약은_바꿀_수_없다() {
			assertThat(rule.lock(stay(101, "1203", DAY.minusDays(2), DAY)))
				.contains(RoomAssignmentRule.Lock.CHECKED_IN);
		}

	}

	private static RoomSnapshot room(String number, int floor, RoomView view) {
		return new RoomSnapshot(number, RoomType.STUDIO, floor, view, RoomStatus.READY, null);
	}

	private static ReservationSnapshot stay(long id, String room, LocalDate checkIn, LocalDate checkOut) {
		return reservation(id, RoomType.STUDIO, room, checkIn, checkOut, ReservationStatus.CHECKED_IN);
	}

	private static ReservationSnapshot reservation(long id, RoomType type, String room, LocalDate checkIn,
			LocalDate checkOut, ReservationStatus status, RoomRequest... requests) {
		return new ReservationSnapshot(id, type, List.of(requests), room, status, checkIn, checkOut,
				LocalTime.of(11, 0), null, null, null, null, null, null, null, null);
	}

}
