package com.urbanstay.earlycheckin.common;

import java.security.SecureRandom;
import java.util.Base64;

/** 추측하기 어려운 고객 링크 토큰과 도어락 비밀번호(목업)를 만든다. */
public final class AccessCodes {

	private static final SecureRandom RANDOM = new SecureRandom();

	private AccessCodes() {
	}

	/** URL에 그대로 쓰는 128비트 토큰 */
	public static String token() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** 6자리 숫자 */
	public static String doorCode() {
		return "%06d".formatted(RANDOM.nextInt(1_000_000));
	}

}
