package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

/**
 * 고객이 고를 수 있는 얼리체크인 입실 시각과 요금.
 *
 * @param immediate "지금 바로" 입실 (정오 전에만). 결제하는 그 시각에 입실한다
 */
public record EarlyCheckInOption(LocalDateTime checkInAt, boolean immediate, int fee) {
}
