package com.urbanstay.earlycheckin.rule;

import java.util.List;

import com.urbanstay.earlycheckin.domain.RoomRequest;

/**
 * 한 예약의 배정 선택지. 점수 대신 요청 항목별 일치 여부와 제외 이유를 그대로 담는다.
 *
 * @param candidates 추천 순서로 정렬한 배정 가능 객실
 * @param excluded 걸러진 객실과 이유 (같은 타입 객실 먼저)
 */
public record AssignmentOptions(List<Candidate> candidates, List<Excluded> excluded) {

	/** @param vacantLastNight 체크인 전날 밤 공실이라 당일 퇴실 청소가 없는 방 */
	public record Candidate(String roomNumber, List<RequestMatch> matches, boolean vacantLastNight) {

		public long matchCount() {
			return matches.stream().filter(RequestMatch::matched).count();
		}

	}

	public record RequestMatch(RoomRequest request, boolean matched) {
	}

	public record Excluded(String roomNumber, List<Exclusion> reasons) {
	}

	/** @param reservationId 기간이 겹치는 예약 (겹침이 아니면 null) */
	public record Exclusion(Reason reason, Long reservationId) {
	}

	public enum Reason {
		ROOM_TYPE_MISMATCH,
		OUT_OF_SERVICE,
		STAY_OVERLAP
	}

}
