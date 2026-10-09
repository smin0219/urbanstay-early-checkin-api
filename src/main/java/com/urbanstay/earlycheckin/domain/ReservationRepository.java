package com.urbanstay.earlycheckin.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	/** 해당 날짜에 걸쳐 있는 예약 (당일 입실·퇴실·연박 포함) */
	@Query("select r from Reservation r where r.checkInDate <= :date and r.checkOutDate >= :date")
	List<Reservation> findStayingOn(LocalDate date);

}
