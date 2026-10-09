package com.urbanstay.earlycheckin.guest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.urbanstay.earlycheckin.domain.OfferStatus;
import com.urbanstay.earlycheckin.domain.Reservation;
import com.urbanstay.earlycheckin.domain.ReservationRepository;
import com.urbanstay.earlycheckin.domain.ReservationStatus;
import com.urbanstay.earlycheckin.evaluation.RuleContexts;
import com.urbanstay.earlycheckin.evaluation.RuleEvaluationService;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.EarlyCheckIn;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.GuestRoom;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.Offer;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.Password;
import com.urbanstay.earlycheckin.guest.GuestCheckInResponse.Stage;
import com.urbanstay.earlycheckin.room.RoomEventService;
import com.urbanstay.earlycheckin.rule.CheckInPolicy;
import com.urbanstay.earlycheckin.rule.CheckInRuleEngine;
import com.urbanstay.earlycheckin.rule.PaymentRejection;

import lombok.RequiredArgsConstructor;

/** 고객 얼리체크인: 정오 안내 링크로 들어와 입실 시각을 골라 결제(목업)하고, 비밀번호를 받아 입실한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestCheckInService {

	private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock clock;

	private final CheckInRuleEngine engine;

	private final CheckInPolicy policy;

	private final ReservationRepository reservationRepository;

	private final RuleEvaluationService evaluationService;

	private final RoomEventService roomEventService;

	public GuestCheckInResponse view(String token) {
		LocalDateTime now = LocalDateTime.now(clock);
		return toResponse(find(token, now), now);
	}

	/**
	 * 얼리체크인 결제(목업). 요금은 결제 시점에 다시 계산하고, 화면에 보였던 요금과 다르면 받지 않는다. 같은 멱등 키로 다시
	 * 요청하면(버튼 중복 클릭) 이미 끝난 결제를 그대로 돌려준다.
	 *
	 * @param checkInAt 고른 입실 시각. null이면 "지금 바로"
	 */
	@Transactional
	public GuestCheckInResponse pay(String token, LocalDateTime checkInAt, int expectedFee, String idempotencyKey) {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation r = find(token, now);
		if (r.getStatus() == ReservationStatus.EARLY_CONFIRMED || r.getOfferStatus() == OfferStatus.ACCEPTED) {
			if (idempotencyKey.equals(r.getPaymentKey())) {
				return toResponse(r, now);
			}
			throw new IllegalStateException("이미 얼리체크인 결제가 완료되었습니다.");
		}
		LocalDateTime chosen = checkInAt == null ? now : checkInAt;
		engine.paymentRejection(now, RuleContexts.snapshot(r), chosen).ifPresent(reason -> {
			throw new IllegalStateException(message(reason));
		});
		int fee = engine.earlyCheckInFee(chosen);
		if (fee != expectedFee) {
			throw new IllegalStateException("시간이 지나 요금이 %,d원으로 바뀌었습니다. 다시 확인해 주세요.".formatted(fee));
		}
		r.confirmEarlyCheckIn(chosen, fee, idempotencyKey, now);
		evaluationService.evaluate();
		return toResponse(r, now);
	}

	/** 얼리체크인을 사지 않고 기본 체크인으로 들어온다. 비밀번호는 일반 발송 규칙을 따른다. */
	@Transactional
	public GuestCheckInResponse decline(String token) {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation r = find(token, now);
		if (r.getStatus() != ReservationStatus.OFFER_SENT) {
			throw new IllegalStateException("거절할 얼리체크인 제안이 없습니다.");
		}
		r.declineOffer();
		evaluationService.evaluate();
		return toResponse(r, now);
	}

	/** 도어락에 비밀번호를 입력해 입실한다 (도어락 목업). */
	@Transactional
	public GuestCheckInResponse openDoor(String token) {
		LocalDateTime now = LocalDateTime.now(clock);
		Reservation r = find(token, now);
		if (r.getStatus() != ReservationStatus.PASSWORD_SENT) {
			throw new IllegalStateException("아직 비밀번호가 발송되지 않았습니다.");
		}
		roomEventService.checkIn(r);
		return toResponse(r, now);
	}

	private GuestCheckInResponse toResponse(Reservation r, LocalDateTime now) {
		Stage stage = stage(r);
		boolean noticed = r.getNoonNoticeSentAt() != null && r.getRoom() != null;
		LocalDateTime passwordAt = switch (stage) {
			case EARLY_CONFIRMED -> r.getEarlyCheckInAt();
			case BEFORE_NOTICE, OFFER_OPEN, WAITING_PASSWORD -> r.getCheckInDate().atTime(policy.passwordSendAt());
			default -> null;
		};
		boolean passwordSent = stage == Stage.PASSWORD_READY || stage == Stage.CHECKED_IN;
		return new GuestCheckInResponse(now, r.getGuestName(), r.getCheckInDate(), r.getCheckOutDate(),
				r.getCheckInDate().atTime(policy.standardCheckInAt()), stage,
				noticed ? new GuestRoom(r.getRoom().getNumber(), r.getRoom().getFloor()) : null,
				stage == Stage.OFFER_OPEN
						? new Offer(engine.offerOptions(now), r.getCheckInDate().atTime(policy.lastEarlyCheckInAt()))
						: null,
				r.getOfferStatus() == OfferStatus.ACCEPTED
						? new EarlyCheckIn(r.getEarlyCheckInAt(), r.getEarlyCheckInFee(), r.getPaidAt()) : null,
				r.getOfferStatus(), passwordAt,
				passwordSent && r.getDoorCode() != null ? new Password(r.getDoorCode(), r.getPasswordSentAt()) : null,
				r.getDelayNoticeSentAt() != null, r.getCheckedInAt());
	}

	private static Stage stage(Reservation r) {
		if (r.getNoonNoticeSentAt() == null) {
			return Stage.BEFORE_NOTICE;
		}
		return switch (r.getStatus()) {
			case OFFER_SENT -> Stage.OFFER_OPEN;
			case EARLY_CONFIRMED -> Stage.EARLY_CONFIRMED;
			case PASSWORD_HELD -> Stage.PASSWORD_HELD;
			case PASSWORD_SENT -> Stage.PASSWORD_READY;
			case CHECKED_IN, CHECKED_OUT -> Stage.CHECKED_IN;
			default -> Stage.WAITING_PASSWORD;
		};
	}

	private String message(PaymentRejection reason) {
		return switch (reason) {
			case OFFER_EXPIRED -> "얼리체크인은 %s까지 신청할 수 있습니다. 비밀번호는 %s에 보내드립니다."
				.formatted(policy.lastEarlyCheckInAt().format(HH_MM), policy.passwordSendAt().format(HH_MM));
			case OFFER_NOT_OPEN -> "결제할 수 있는 얼리체크인 제안이 없습니다.";
			case TIME_PASSED -> "선택한 입실 시각이 지났습니다. 다시 선택해 주세요.";
			case INVALID_TIME -> "선택할 수 없는 입실 시각입니다.";
		};
	}

	private Reservation find(String token, LocalDateTime now) {
		Reservation r = reservationRepository.findByAccessToken(token)
			.orElseThrow(() -> new NoSuchElementException("유효하지 않은 링크입니다."));
		if (!r.isAccessibleAt(now)) {
			throw new NoSuchElementException("체크아웃 시각이 지나 링크가 만료되었습니다.");
		}
		return r;
	}

}
