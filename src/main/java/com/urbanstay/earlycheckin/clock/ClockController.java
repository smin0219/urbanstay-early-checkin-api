package com.urbanstay.earlycheckin.clock;

import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/clock")
@RequiredArgsConstructor
public class ClockController {

	private final ClockService clockService;

	@GetMapping
	public ClockResponse now() {
		return new ClockResponse(clockService.now());
	}

	@PostMapping("/advance")
	public ClockResponse advance(@Valid @RequestBody AdvanceRequest request) {
		return new ClockResponse(clockService.advanceTo(request.to()));
	}

	public record AdvanceRequest(@NotNull LocalDateTime to) {
	}

}
