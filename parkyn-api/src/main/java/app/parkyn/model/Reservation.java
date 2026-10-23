package app.parkyn.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Entreprise propriétaire. @TenantId : Hibernate filtre toutes les requêtes sur le tenant de la session
    // et le renseigne à l'insertion (voir CurrentTenantResolver). Jamais modifié après la création.
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false, referencedColumnName = "id")
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", referencedColumnName = "id")
    private User agent;

    @Column(name = "start_date", nullable = false)
    private OffsetDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private OffsetDateTime endDate;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_before", length = 20, nullable = false)
    private Condition conditionBefore;

    // Saisi à la remise des clés (vide avant)
    @Column(name = "mileage_start")
    private Integer mileageStart;

    @Column(name = "notes_before")
    private String notesBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_after", length = 20)
    private Condition conditionAfter;

    @Column(name = "mileage_end")
    private Integer mileageEnd;

    @Column(name = "damage_reported")
    private Boolean damageReported;

    @Column(name = "damage_details")
    private String damageDetails;

    @Column(name = "notes_after")
    private String notesAfter;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp 
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id", updatable = false, nullable = true)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id", nullable = true)
    private User updatedBy;

    // Remise du véhicule (état des lieux de départ et remise des clés) : la réservation est alors en cours
    @Column(name = "handed_over_at")
    private OffsetDateTime handedOverAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handed_over_by", referencedColumnName = "id", nullable = true)
    private User handedOverBy;

    // Annulation douce : la réservation reste dans l'historique, elle ne bloque plus son créneau
    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", referencedColumnName = "id", nullable = true)
    private User cancelledBy;

    // Statut calculé, jamais stocké. En cours = véhicule remis. Avant la remise, une réservation dont
    // le rendez-vous est passé est « à remettre », ou en attente si le véhicule est encore dehors avec
    // une autre réservation (onHold, voir ReservationRepository.findOnHoldIds)
    public Status statusAt(OffsetDateTime now, boolean onHold) {
        if (cancelledAt != null) return Status.CANCELLED;
        if (returnedAt != null) return Status.RETURNED;
        if (handedOverAt != null) return Status.IN_PROGRESS;
        if (startDate.isAfter(now)) return Status.UPCOMING;
        return onHold ? Status.ON_HOLD : Status.AWAITING_HANDOVER;
    }

    public boolean isHandedOver() {
        return handedOverAt != null;
    }

    public boolean hasStartedAt(OffsetDateTime now) {
        return !startDate.isAfter(now);
    }

    public enum Status {
        UPCOMING, AWAITING_HANDOVER, ON_HOLD, IN_PROGRESS, RETURNED, CANCELLED
    }

    public enum Condition {
        EXCELLENT, GOOD, FAIR, POOR
    }
}
