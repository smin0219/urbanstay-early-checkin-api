package com.urbanstay.earlycheckin.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.clock.ClockService;
import com.urbanstay.earlycheckin.common.AccessCodes;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.Room;
import com.urbanstay.earlycheckin.domain.RoomEvent;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.domain.RoomStatusChange;
import com.urbanstay.earlycheckin.domain.RoomStatusChangeRepository;
import com.urbanstay.earlycheckin.evaluation.RuleEvaluationService;
import com.urbanstay.earlycheckin.rule.CheckInPolicy;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * sample-data.json을 적재한다. 애플리케이션 시작 시와 시나리오 초기화 API가 모두 이 로더를 쓴다. 기존 데이터를 지우고
 * 가상 시계도 시작 시각으로 되돌린 뒤, 시작 시각 기준으로 규칙을 평가한다.
 */
@Component
@RequiredArgsConstructor
public class SampleDataLoader implements ApplicationRunner {

	private static final String SAMPLE_DATA = "sample-data.json";

	private final JsonMapper jsonMapper;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final MessageLogRepository messageLogRepository;

	private final RoomStatusChangeRepository roomStatusChangeRepository;

	private final ClockService clockService;

	private final CheckInPolicy policy;

	private final RuleEvaluationService evaluationService;

	private final EntityManager entityManager;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		load();
	}

	@Transactional
	public void load() {
		messageLogRepository.deleteAllInBatch();
		roomStatusChangeRepository.deleteAllInBatch();
		reservationRepository.deleteAll();
		reservationRepository.flush();
		roomRepository.deleteAllInBatch();
		// 지운 예약이 들고 있던 객실 프록시가 남아 있으면 같은 호수로 다시 저장할 때 충돌한다
		entityManager.clear();
		clockService.reset();

		SampleData data = read();
		Map<String, Room> rooms = roomRepository
			.saveAll(data.rooms().stream()
				.map(r -> new Room(r.number(), r.type(), r.floor(), r.view(), r.status(), r.readyAt()))
				.toList())
			.stream()
			.collect(Collectors.toMap(Room::getNumber, Function.identity()));
		reservationRepository.saveAll(data.reservations().stream().map(r -> toReservation(r, rooms)).toList());
		// 상태 타임라인의 시작점
		LocalDateTime start = clockService.now();
		roomStatusChangeRepository.saveAll(rooms.values()
			.stream()
			.map(room -> new RoomStatusChange(room.getNumber(), null, room.getStatus(), RoomEvent.INITIAL, start, null))
			.toList());
		// 시작 시각에 이미 준비된 방은 바로 얼리체크인 제안을 받는다
		evaluationService.evaluate();
	}

	private Reservation toReservation(SampleData.ReservationData r, Map<String, Room> rooms) {
		Room room = null;
		if (r.roomNumber() != null) {
			room = rooms.get(r.roomNumber());
			if (room == null) {
				throw new IllegalStateException("샘플 예약 " + r.id() + "의 객실 " + r.roomNumber() + "이(가) 없습니다");
			}
		}
		return new Reservation(r.id(), r.guestName(), r.roomType(), r.checkInDate(), r.checkOutDate(),
				r.checkOutTime() != null ? r.checkOutTime() : policy.standardCheckOutAt(),
				r.requests() != null ? r.requests() : List.of(), room, r.status(), r.checkedOutAt(), AccessCodes.token());
	}

	private SampleData read() {
		try (InputStream in = new ClassPathResource(SAMPLE_DATA).getInputStream()) {
			return jsonMapper.readValue(in, SampleData.class);
		}
		catch (IOException e) {
			throw new UncheckedIOException(SAMPLE_DATA + "을(를) 읽을 수 없습니다", e);
		}
	}

}
