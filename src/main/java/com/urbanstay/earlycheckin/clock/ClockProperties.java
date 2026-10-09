package com.urbanstay.earlycheckin.clock;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

/** @param start 가상 시계 시작 시각 (시나리오 초기화 시 이 시각으로 돌아간다) */
@ConfigurationProperties("urbanstay.clock")
public record ClockProperties(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start, ZoneId zone) {
}
