package com.urbanstay.earlycheckin.evaluation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.common.AccessCodes;
import com.urbanstay.earlycheckin.domain.MessageLog;
import com.urbanstay.earlycheckin.domain.MessageLogRepository;
import com.urbanstay.earlycheckin.domain.MessageType;
import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.RoomRepository;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.RuleAction;
import com.urbanstay.earlycheckin.rule.RuleAction.ExpireOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.HoldPassword;
import com.urbanstay.earlycheckin.rule.RuleAction.SendDelayNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendEarlyOffer;
import com.urbanstay.earlycheckin.rule.RuleAction.SendNoonNotice;
import com.urbanstay.earlycheckin.rule.RuleAction.SendPassword;
import com.urbanstay.earlycheckin.rule.RuleContext;

import lombok.RequiredArgsConstructor;

/**
 * 규칙 엔진을 돌리고 결과(상태 변경, 발송 기록)를 반영한다. 스케줄러 없이 시각 이동과 이벤트(퇴실, 청소 완료, 결제)
 * 처리 뒤에 호출한다.
 */
@Service
@RequiredArgsConstructor
public class RuleEvaluationService {

	/** 한 번 적용하면 다음 단계가 생길 수 있다 (예: 제안 만료 → 비밀번호 발송). 무한 반복 방지용 상한. */
	private static final int MAX_PASSES = 10;

	private final Clock clock;

	private final CheckInRuleEngine engine;

	private final RoomRepository roomRepository;

	private final ReservationRepository reservationRepository;

	private final MessageLogRepository messageLogRepository;

	@Transactional
	public void evaluate() {
		LocalDateTime now = LocalDateTime.now(clock);
		for (int pass = 0; pass < MAX_PASSES; pass++) {
			List<Reservation> reservations = reservationRepository.findStayingOn(now.toLocalDate());
			RuleContext ctx = RuleContexts.of(now, roomRepository.findAll(), reservations);
			List<RuleAction> actions = engine.evaluate(ctx);
			if (actions.isEmpty()) {
				return;
			}
			Map<Long, Reservation> byId = reservations.stream()
				.collect(Collectors.toMap(Reservation::getId, Function.identity()));
			actions.forEach(action -> apply(action, byId.get(action.reservationId()), now));
		}
		throw new IllegalStateException("규칙 평가가 " + MAX_PASSES + "회 안에 끝나지 않았습니다");
	}

	/** {@code from}에서 {@code to}로 시계를 옮길 때 거쳐 가며 평가해야 하는 시각 */
	@Transactional(readOnly = true)
	public List<LocalDateTime> checkpointsBetween(LocalDateTime from, LocalDateTime to) {
		RuleContext ctx = RuleContexts.of(from, roomRepository.findAll(),
				reservationRepository.findStayingOn(from.toLocalDate()));
		return engine.checkpoints(ctx, from, to);
	}

	private void apply(RuleAction action, Reservation r, LocalDateTime now) {
		String room = r.getRoom().getNumber();
		switch (action) {
			case SendNoonNotice notice -> {
				r.sendNoonNotice(now);
				String content = "[어반스테이] " + r.getGuestName() + "님, 오늘 객실은 " + room
						+ "호입니다. 입실 방법: 1층 공동현관 → 엘리베이터 → " + room + "호 도어락. 비밀번호는 객실 준비 후 따로 보내드립니다.";
				if (notice.withOffer()) {
					r.sendOffer(now);
					content += " 객실이 준비되어 있어 얼리체크인을 신청하면 일찍 입실할 수 있습니다.";
				}
				content += " 체크인 안내: " + checkInLink(r);
				log(r, MessageType.NOON_NOTICE, now, content);
			}
			case SendEarlyOffer offer -> {
				r.sendOffer(now);
				log(r, MessageType.EARLY_OFFER, now, "[어반스테이] " + room
						+ "호가 준비되었습니다. 얼리체크인을 신청하면 일찍 입실할 수 있습니다. 신청: " + checkInLink(r));
			}
			case ExpireOffer expire -> r.expireOffer();
			case SendPassword password -> {
				r.sendPassword(now, AccessCodes.doorCode());
				log(r, MessageType.PASSWORD, now,
						"[어반스테이] " + room + "호 도어락 비밀번호를 보내드립니다. 확인: " + checkInLink(r));
			}
			case HoldPassword hold -> r.holdPassword(now);
			case SendDelayNotice delay -> {
				r.sendDelayNotice(now);
				log(r, MessageType.DELAY_NOTICE, now,
						"[어반스테이] " + room + "호 준비가 늦어지고 있습니다. 준비가 끝나는 즉시 비밀번호를 보내드리겠습니다.");
			}
		}
	}

	private static String checkInLink(Reservation r) {
		return "/check-in/" + r.getAccessToken();
	}

	private void log(Reservation reservation, MessageType type, LocalDateTime now, String content) {
		messageLogRepository.save(new MessageLog(reservation, type, now, content));
	}

}
