package com.urbanstay.earlycheckin.room;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 객실 운영 상세와 객실 이벤트. 이벤트 API는 처리 후 갱신된 상세를 돌려준다. */
@RestController
@RequestMapping("/api/rooms/{number}")
@RequiredArgsConstructor
public class RoomController {

	private final RoomService roomService;

	private final RoomEventService roomEventService;

	@GetMapping
	public RoomDetailResponse detail(@PathVariable String number) {
		return roomService.detail(number);
	}

	@PostMapping("/checkout")
	public RoomDetailResponse checkOut(@PathVariable String number) {
		roomEventService.checkOut(number);
		return roomService.detail(number);
	}

	@PostMapping("/cleaning/start")
	public RoomDetailResponse startCleaning(@PathVariable String number) {
		roomEventService.startCleaning(number);
		return roomService.detail(number);
	}

	@PostMapping("/cleaning/finish")
	public RoomDetailResponse finishCleaning(@PathVariable String number) {
		roomEventService.finishCleaning(number);
		return roomService.detail(number);
	}

	/** 준비 완료 후 문제 발견 (기획상 화면 버튼 없이 API·테스트로만 확인) */
	@PostMapping("/issue")
	public RoomDetailResponse reportIssue(@PathVariable String number) {
		roomEventService.reportIssue(number);
		return roomService.detail(number);
	}

}
