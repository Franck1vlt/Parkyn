package app.parkyn.model;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Entreprise propriétaire. @TenantId : Hibernate filtre toutes les requêtes sur le tenant de la session
    // et le renseigne à l'insertion (voir CurrentTenantResolver). Jamais modifié après la création.
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "first_name", length = 80, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 80, nullable = false)
    private String lastName;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "hashed_pwd")
    private String hashedPwd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "is_active")
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT NOW()")
    private OffsetDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<app.parkyn.model.Role> roles = new HashSet<>();

    /**
     * Les autorités sont reconstruites par JwtAuthFilter à partir des permissions
     * mises en cache par PermissionService ; celles de l'entité ne sont donc pas utilisées.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        return Set.of();
    }

    @Override
    public String getPassword() { 
        return hashedPwd;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() { return status == Status.ACTIVE; }

    public void setStatus(Status s) {
        this.status = s;
        this.active = (s == Status.ACTIVE);
    }

    public void setActive(boolean b) {
        setStatus(b ? Status.ACTIVE : Status.DISABLED);
    }

    public enum Role { ADMIN, AGENT, DRIVER }

    public enum Status { PENDING_EMAIL_VERIFICATION, ACTIVE, DISABLED }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Role getRole() {
        return roles.stream()
            .filter(r -> r.getTenantId() == null)
            .map(r -> Role.valueOf(r.getName()))
            .findFirst()
            .orElse(null);
    }
}
