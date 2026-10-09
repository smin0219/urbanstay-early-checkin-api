package com.urbanstay.earlycheckin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.urbanstay.earlycheckin.reservation.ReservationDetailResponse;

/** 통합 테스트에서 시나리오를 API로 재생하는 도우미. 시각은 데모일(2026-10-10)의 "HH:mm"으로 쓴다. */
final class ScenarioApi {

	static final LocalDate DAY = LocalDate.of(2026, 10, 10);

	private final MockMvcTester mvc;

	ScenarioApi(MockMvcTester mvc) {
		this.mvc = mvc;
	}

	static LocalDateTime at(String time) {
		return DAY.atTime(LocalTime.parse(time));
	}

	void reset() {
		assertThat(post("/api/scenario/reset", null)).hasStatusOk();
	}

	void advanceTo(String time) {
		assertThat(post("/api/clock/advance", "{\"to\": \"" + at(time) + "\"}")).hasStatusOk();
	}

	/** 시계를 옮긴 뒤 요청을 보낸다 */
	MvcTestResult postAt(String time, String uri, String json) {
		advanceTo(time);
		return post(uri, json);
	}

	MvcTestResult post(String uri, String json) {
		var request = mvc.post().uri(uri);
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return request.exchange();
	}

	<T> T get(String uri, Class<T> type) {
		return mvc.get().uri(uri).exchange().assertThat().hasStatusOk().bodyJson().convertTo(type).actual();
	}

	<T> T read(MvcTestResult result, Class<T> type) {
		return result.assertThat().hasStatusOk().bodyJson().convertTo(type).actual();
	}

	String checkInToken(long reservationId) {
		return get("/api/reservations/" + reservationId, ReservationDetailResponse.class).reservation().checkInToken();
	}

}
