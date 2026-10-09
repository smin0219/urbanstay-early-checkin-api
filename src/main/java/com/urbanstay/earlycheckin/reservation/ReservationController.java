package com.urbanstay.earlycheckin.reservation;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

	@GetMapping("/{id}")
	public ReservationDetailResponse detail(@PathVariable long id) {
		return reservationService.detail(id);
	}

	@PostMapping("/{id}/assignment")
	public ReservationDetailResponse assign(@PathVariable long id, @Valid @RequestBody AssignRequest request) {
		return reservationService.assign(id, request.roomNumber());
	}

	@DeleteMapping("/{id}/assignment")
	public ReservationDetailResponse unassign(@PathVariable long id) {
		return reservationService.unassign(id);
	}

	public record AssignRequest(@NotBlank String roomNumber) {
	}

}
