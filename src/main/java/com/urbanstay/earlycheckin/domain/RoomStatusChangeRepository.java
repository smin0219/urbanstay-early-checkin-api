package com.urbanstay.earlycheckin.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomStatusChangeRepository extends JpaRepository<RoomStatusChange, Long> {

	List<RoomStatusChange> findByRoomNumberOrderByChangedAtAscIdAsc(String roomNumber);

}
