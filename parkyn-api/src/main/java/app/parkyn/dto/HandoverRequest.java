package app.parkyn.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// État des lieux de départ (décision H1) : seul le kilométrage est saisi, l'état est repris du véhicule
public record HandoverRequest(
    @NotNull @PositiveOrZero Integer mileageStart
) {}
