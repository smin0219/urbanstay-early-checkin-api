package com.urbanstay.earlycheckin.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

	long countByTypeAndRoomStatusNot(MessageType type, RoomStatus roomStatus);

	List<MessageLog> findByRoomNumberOrderBySentAtAscIdAsc(String roomNumber);

}
