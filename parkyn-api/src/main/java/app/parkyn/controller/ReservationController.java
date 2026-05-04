package app.parkyn.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.ReservationResponse;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.model.Reservation;
import app.parkyn.service.ReservationService;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("")
    public ResponseEntity<Page<ReservationResponse>> getRerservations(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID vehicleId,
        @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok().body(reservationService.getReservations(status, vehicleId, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PostMapping("")
    public ResponseEntity<ReservationResponse> addReservation(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.addReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable UUID id) {
        ReservationResponse response = reservationService.getReservationById(id);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PostMapping("/{id}/return")
    public ResponseEntity<ReservationResponse> returnVehicle(@PathVariable UUID id, @Valid @RequestBody ReturnVehicleRequest request) {
        ReservationResponse response = reservationService.returnVehicle(id, request);
        return ResponseEntity.ok().body(response);
    }
}
