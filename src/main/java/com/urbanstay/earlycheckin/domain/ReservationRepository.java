package com.urbanstay.earlycheckin.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	/** 해당 날짜에 걸쳐 있는 예약 (당일 입실·퇴실·연박 포함) */
	@Query("select r from Reservation r where r.checkInDate <= :date and r.checkOutDate >= :date")
	List<Reservation> findStayingOn(LocalDate date);

	/** [checkIn, checkOut) 기간과 겹치거나 checkIn 전날 밤에 묵은 예약 (배정 후보 판단용) */
	@Query("select r from Reservation r where r.checkInDate < :checkOut and r.checkOutDate >= :checkIn")
	List<Reservation> findAround(LocalDate checkIn, LocalDate checkOut);

}
