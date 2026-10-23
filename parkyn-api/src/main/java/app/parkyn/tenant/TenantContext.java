package app.parkyn.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant fixé explicitement, pour un traitement sans utilisateur connecté (ex. traitement planifié).
 * Une session Hibernate lit son tenant à l'ouverture : la transaction doit être ouverte DANS runAs.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
        // classe utilitaire : uniquement des méthodes static, jamais instanciée
    }

    public static void runAs(UUID tenantId, Runnable action) {
        UUID previous = CURRENT.get();
        CURRENT.set(Objects.requireNonNull(tenantId, "tenantId"));
        try {
            action.run();
        } finally {
            // Toujours retiré, même en cas d'erreur : le thread (pool du planificateur) sera réutilisé
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static UUID current() {
        return CURRENT.get();
    }
}
