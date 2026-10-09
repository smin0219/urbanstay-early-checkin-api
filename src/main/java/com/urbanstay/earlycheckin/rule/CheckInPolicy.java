package com.urbanstay.earlycheckin.rule;

import java.time.Duration;
import java.time.LocalTime;

/**
 * 체크인 운영 정책 값. 가설이 바뀌어도 구조를 그대로 쓸 수 있도록 시각·기준은 모두 설정에서 받는다.
 *
 * @param noonNoticeAt 정오 안내 발송 시각
 * @param lastEarlyCheckInAt 얼리체크인으로 입실할 수 있는 마지막 시각 (이후 제안 만료)
 * @param cleaningWarningAt 미준비 객실 운영자 경고 시각
 * @param passwordSendAt 비밀번호 발송 시각 (얼리 확정이 아닌 예약)
 * @param standardCheckInAt 기본 체크인 시각 (이후에도 미준비면 지연 안내·경고 상향)
 * @param standardCheckOutAt 기본 퇴실 시각
 * @param earlyCheckoutThreshold 예정 퇴실보다 이만큼 이상 일찍 나가면 조기 퇴실로 본다
 */
public record CheckInPolicy(
		LocalTime noonNoticeAt,
		LocalTime lastEarlyCheckInAt,
		LocalTime cleaningWarningAt,
		LocalTime passwordSendAt,
		LocalTime standardCheckInAt,
		LocalTime standardCheckOutAt,
		Duration earlyCheckoutThreshold) {
}
