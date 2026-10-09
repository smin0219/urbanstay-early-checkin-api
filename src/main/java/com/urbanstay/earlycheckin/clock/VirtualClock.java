package com.urbanstay.earlycheckin.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** 데모용 가상 시계. 시각 이동 API로만 움직이고, 실제 시간이 흘러도 멈춰 있다. */
public class VirtualClock extends Clock {

	private final ZoneId zone;

	private final AtomicReference<Instant> instant;

	public VirtualClock(LocalDateTime start, ZoneId zone) {
		this.zone = zone;
		this.instant = new AtomicReference<>(start.atZone(zone).toInstant());
	}

	public void setTo(LocalDateTime time) {
		instant.set(time.atZone(zone).toInstant());
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instant(), zone);
	}

	@Override
	public Instant instant() {
		return instant.get();
	}

}
