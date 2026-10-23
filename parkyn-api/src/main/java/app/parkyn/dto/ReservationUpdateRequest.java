package app.parkyn.dto;

import java.time.OffsetDateTime;

// Champs modifiables d'une réservation (décision R4), tous facultatifs.
// Changer de véhicule ou de conducteur : annuler puis créer une nouvelle réservation.
public record ReservationUpdateRequest(
    OffsetDateTime startDate,
    OffsetDateTime endDate,
    String notesBefore
) {}
