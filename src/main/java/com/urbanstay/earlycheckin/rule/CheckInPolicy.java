package com.urbanstay.earlycheckin.rule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 체크인 운영 정책 값. 가설이 바뀌어도 구조를 그대로 쓸 수 있도록 시각·기준은 모두 설정에서 받는다.
 *
 * @param noonNoticeAt 정오 안내 발송 시각
 * @param lastEarlyCheckInAt 얼리체크인으로 입실할 수 있는 마지막 정각 (이 시각이 되면 제안 만료)
 * @param cleaningWarningAt 미준비 객실 운영자 경고 시각
 * @param passwordLead 입실 시각보다 이만큼 먼저 비밀번호를 보낸다 (기본 체크인이든 얼리체크인이든 같다)
 * @param standardCheckInAt 기본 체크인 시각 (이후에도 미준비면 지연 안내·경고 상향)
 * @param standardCheckOutAt 기본 퇴실 시각
 * @param earlyCheckoutThreshold 예정 퇴실보다 이만큼 이상 일찍 나가면 조기 퇴실로 본다
 * @param highFloorFrom 이 층 이상이면 고층 요청에 일치한다
 * @param earlyCheckInFeePerHour 얼리체크인 시간당 요금 (기본 체크인 시각까지 남은 시간을 곱한다)
 * @param earlyCheckInMaxHours 요금을 매기는 최대 시간. 이보다 이른 입실도 같은 요금 (4시간이면 정오 전 입실은 모두 40,000원)
 * @param offerStartAt 얼리체크인 제안을 보내기 시작하는 시각 (이전부터 준비된 방은 이 시각에 제안)
 * @param immediateCheckInUntil 이 시각 전에는 "지금 바로" 입실을 팔고, 이 시각부터는 정각 단위로 판다
 * @param earlyCheckInMinLead 다음 정각까지 이 시간 이하로 남으면 그 정각은 건너뛰고 다음 정각부터 판다
 */
public record CheckInPolicy(
		LocalTime noonNoticeAt,
		LocalTime lastEarlyCheckInAt,
		LocalTime cleaningWarningAt,
		Duration passwordLead,
		LocalTime standardCheckInAt,
		LocalTime standardCheckOutAt,
		Duration earlyCheckoutThreshold,
		int highFloorFrom,
		int earlyCheckInFeePerHour,
		int earlyCheckInMaxHours,
		LocalTime offerStartAt,
		LocalTime immediateCheckInUntil,
		Duration earlyCheckInMinLead) {

	/** 입실 시각이 {@code checkInAt}인 예약의 비밀번호 발송 시각 */
	public LocalDateTime passwordSendAt(LocalDateTime checkInAt) {
		return checkInAt.minus(passwordLead);
	}

	/** 기본 체크인 시각에 들어오는 예약의 비밀번호 발송 시각 (기본값 14:45) */
	public LocalTime standardPasswordSendAt() {
		return standardCheckInAt.minus(passwordLead);
	}

}
