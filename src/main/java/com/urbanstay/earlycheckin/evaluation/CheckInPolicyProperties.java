package com.urbanstay.earlycheckin.evaluation;

import java.time.Duration;
import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import com.urbanstay.earlycheckin.rule.CheckInPolicy;

@ConfigurationProperties("urbanstay.policy")
public record CheckInPolicyProperties(
		@DateTimeFormat(pattern = "HH:mm") LocalTime noonNoticeAt,
		@DateTimeFormat(pattern = "HH:mm") LocalTime lastEarlyCheckInAt,
		@DateTimeFormat(pattern = "HH:mm") LocalTime cleaningWarningAt,
		@DateTimeFormat(pattern = "HH:mm") LocalTime passwordSendAt,
		@DateTimeFormat(pattern = "HH:mm") LocalTime standardCheckInAt,
		@DateTimeFormat(pattern = "HH:mm") LocalTime standardCheckOutAt,
		Duration earlyCheckoutThreshold) {

	public CheckInPolicy toPolicy() {
		return new CheckInPolicy(noonNoticeAt, lastEarlyCheckInAt, cleaningWarningAt, passwordSendAt,
				standardCheckInAt, standardCheckOutAt, earlyCheckoutThreshold);
	}

}
