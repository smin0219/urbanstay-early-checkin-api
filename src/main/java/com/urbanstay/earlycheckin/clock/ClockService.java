package com.urbanstay.earlycheckin.clock;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.clock.ClockResponse.Milestone;
import com.urbanstay.earlycheckin.evaluation.RuleEvaluationService;
import com.urbanstay.earlycheckin.rule.CheckInPolicy;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClockService {

	private static final Duration MAX_ADVANCE = Duration.ofDays(1);

	private final VirtualClock clock;

	private final ClockProperties properties;

	private final RuleEvaluationService evaluationService;

	private final CheckInPolicy policy;

	public LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/**
	 * 시계를 앞으로 옮긴다. 사이에 있는 규칙 시각(정오 안내, 비밀번호 발송 등)을 차례로 거치며 평가하므로, 한 번에 크게
	 * 건너뛰어도 각 발송은 원래 시각으로 기록된다.
	 */
	@Transactional
	public LocalDateTime advanceTo(LocalDateTime target) {
		LocalDateTime now = now();
		if (!target.isAfter(now)) {
			throw new IllegalArgumentException("가상 시계는 앞으로만 이동할 수 있습니다. 되돌리려면 시나리오를 초기화하세요.");
		}
		if (Duration.between(now, target).compareTo(MAX_ADVANCE) > 0) {
			throw new IllegalArgumentException("한 번에 하루 넘게 이동할 수 없습니다.");
		}
		for (LocalDateTime checkpoint : evaluationService.checkpointsBetween(now, target)) {
			clock.setTo(checkpoint);
			evaluationService.evaluate();
		}
		clock.setTo(target);
		evaluationService.evaluate();
		return target;
	}

	public void reset() {
		clock.setTo(properties.start());
	}

	/** 현재 시각과 오늘의 규칙 시각 (정책 설정값) */
	public ClockResponse current() {
		LocalDateTime now = now();
		LocalDate day = now.toLocalDate();
		return new ClockResponse(now, List.of(
				new Milestone(day.atTime(policy.offerStartAt()), Milestone.Type.OFFER_START),
				new Milestone(day.atTime(policy.noonNoticeAt()), Milestone.Type.NOON_NOTICE),
				new Milestone(day.atTime(policy.cleaningWarningAt()), Milestone.Type.CLEANING_WARNING),
				new Milestone(day.atTime(policy.lastEarlyCheckInAt()), Milestone.Type.OFFER_EXPIRY),
				new Milestone(day.atTime(policy.standardPasswordSendAt()), Milestone.Type.PASSWORD_SEND),
				new Milestone(day.atTime(policy.standardCheckInAt()), Milestone.Type.STANDARD_CHECK_IN)));
	}

}
