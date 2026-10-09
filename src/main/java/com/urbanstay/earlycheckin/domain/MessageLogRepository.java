package com.urbanstay.earlycheckin.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

	long countByTypeAndRoomStatusNot(MessageType type, RoomStatus roomStatus);

}
