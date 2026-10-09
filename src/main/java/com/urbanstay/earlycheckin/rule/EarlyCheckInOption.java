package com.urbanstay.earlycheckin.rule;

import java.time.LocalDateTime;

/**
 * 고객이 고를 수 있는 얼리체크인 입실 시각과 요금.
 *
 * @param immediate "지금 바로" 선택지 (결제 시점의 현재 시각으로 입실)
 */
public record EarlyCheckInOption(LocalDateTime checkInAt, boolean immediate, int fee) {
}
