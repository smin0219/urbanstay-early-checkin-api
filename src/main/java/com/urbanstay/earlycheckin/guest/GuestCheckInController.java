package com.urbanstay.earlycheckin.guest;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/** 고객 화면 API. 정오 안내 문자에 담긴 토큰 링크로만 접근한다. */
@RestController
@RequestMapping("/api/check-in/{token}")
@RequiredArgsConstructor
public class GuestCheckInController {

	private final GuestCheckInService guestCheckInService;

	@GetMapping
	public GuestCheckInResponse view(@PathVariable String token) {
		return guestCheckInService.view(token);
	}

	@PostMapping("/payment")
	public GuestCheckInResponse pay(@PathVariable String token, @Valid @RequestBody PaymentRequest request) {
		return guestCheckInService.pay(token, request.checkInAt(), request.expectedFee(), request.idempotencyKey());
	}

	@PostMapping("/decline")
	public GuestCheckInResponse decline(@PathVariable String token) {
		return guestCheckInService.decline(token);
	}

	@PostMapping("/door")
	public GuestCheckInResponse openDoor(@PathVariable String token) {
		return guestCheckInService.openDoor(token);
	}

	/**
	 * @param checkInAt 고른 입실 시각. 없으면 "지금 바로"
	 * @param expectedFee 화면에 보였던 요금. 결제 시점 요금과 다르면 결제하지 않는다
	 * @param idempotencyKey 같은 결제 시도를 구분하는 키 (중복 클릭 방지)
	 */
	public record PaymentRequest(LocalDateTime checkInAt, @PositiveOrZero int expectedFee,
			@NotBlank String idempotencyKey) {
	}

}
