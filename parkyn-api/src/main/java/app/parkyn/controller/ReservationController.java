package app.parkyn.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.ReservationRequest;
import app.parkyn.dto.HandoverRequest;
import app.parkyn.dto.ReservationResponse;
import app.parkyn.dto.ReservationUpdateRequest;
import app.parkyn.dto.ReturnVehicleRequest;
import app.parkyn.dto.UserRequest;
import app.parkyn.model.Reservation;
import app.parkyn.model.User;
import app.parkyn.service.ReservationService;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PreAuthorize("hasAnyAuthority('reservation:read:all', 'reservation:read:own')")
    @GetMapping("")
    public ResponseEntity<Page<ReservationResponse>> getRerservations(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID vehicleId,
        @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok().body(reservationService.getReservations(currentUser, status, vehicleId, pageable));
    }

    @PreAuthorize("hasAnyAuthority('reservation:create:all', 'reservation:create:own')")
    @PostMapping("")
    public ResponseEntity<ReservationResponse> addReservation(
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody ReservationRequest request
    ) {
        ReservationResponse response = reservationService.addReservation(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyAuthority('reservation:read:all', 'reservation:read:own')")
    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        ReservationResponse response = reservationService.getReservationById(currentUser, id);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize("hasAnyAuthority('reservation:update:all', 'reservation:update:own')")
    @PatchMapping("/{id}")
    public ResponseEntity<ReservationResponse> updateReservation(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id,
        @Valid @RequestBody ReservationUpdateRequest request
    ) {
        return ResponseEntity.ok().body(reservationService.updateReservation(currentUser, id, request));
    }

    @PreAuthorize("hasAnyAuthority('reservation:cancel:all', 'reservation:cancel:own')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok().body(reservationService.cancelReservation(currentUser, id));
    }

    @PreAuthorize("hasAuthority('reservation:handover:all')")
    @PostMapping("/{id}/handover")
    public ResponseEntity<ReservationResponse> handOver(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id,
        @Valid @RequestBody HandoverRequest request
    ) {
        return ResponseEntity.ok().body(reservationService.handOver(currentUser, id, request));
    }

    @PreAuthorize("hasAuthority('reservation:return:all')")
    @PostMapping("/{id}/return")
    public ResponseEntity<ReservationResponse> returnVehicle(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id, 
        @Valid @RequestBody ReturnVehicleRequest request
    ) {
        ReservationResponse response = reservationService.returnVehicle(currentUser, id, request);
        return ResponseEntity.ok().body(response);
    }
}
