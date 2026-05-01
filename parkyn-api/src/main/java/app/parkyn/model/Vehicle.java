package app.parkyn.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plate_number", length = 20, nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "brand", length = 80, nullable = false)
    private String brand;

    @Column(name = "model", length = 80, nullable = false)
    private String model;

    @Column(name = "year")
    private Integer year;

    @Column(name = "mileage_km", nullable = false)
    private Integer mileageKM = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", length = 20)
    private VehicleCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private VehicleStatus status = VehicleStatus.AVAILABLE;

    @Column(name = "commissioned_at")
    private LocalDate commissionedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    public enum VehicleStatus {
        AVAILABLE, RESERVED, MAINTENANCE, RETIRED
    }

    public enum VehicleCondition {
        EXCELLENT, GOOD, FAIR, POOR
    }
}
