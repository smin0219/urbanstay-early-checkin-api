package com.urbanstay.earlycheckin.scenario;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urbanstay.earlycheckin.clock.ClockResponse;
import com.urbanstay.earlycheckin.clock.ClockService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scenario")
@RequiredArgsConstructor
public class ScenarioController {

	private final SampleDataLoader sampleDataLoader;

	private final ClockService clockService;

	/** 샘플 데이터와 가상 시계를 처음 상태로 되돌린다. */
	@PostMapping("/reset")
	public ClockResponse reset() {
		sampleDataLoader.load();
		return clockService.current();
	}

}
