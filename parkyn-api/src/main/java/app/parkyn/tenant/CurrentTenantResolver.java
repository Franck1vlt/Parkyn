package app.parkyn.tenant;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import app.parkyn.model.User;

/**
 * Donne à Hibernate le tenant de chaque session, lu à son ouverture. Ce tenant filtre toutes les requêtes
 * des entités @TenantId (User, Vehicle, Reservation, Notification), findById compris :
 * - tenant fixé explicitement (TenantContext.runAs) : ce tenant ;
 * - utilisateur connecté : son tenant ;
 * - aucun utilisateur (connexion, inscription, traitement planifié) : ROOT, sans filtre.
 *   Le code fixe alors lui-même le tenant des lignes qu'il crée, sinon la base refuse l'insertion
 *   (Hibernate écrit toujours la colonne ; en SQL direct, le DEFAULT temporaire s'applique encore jusqu'au Contract).
 * Tout autre cas lève une exception : jamais de repli silencieux vers « tous les tenants ».
 *
 * Limite : les requêtes SQL natives (nativeQuery = true) ne sont pas filtrées.
 */
@Component
public class CurrentTenantResolver implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    // Ne correspond à aucune ligne de tenants : une insertion sans tenant échoue sur la clé étrangère
    static final UUID ROOT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID explicit = TenantContext.current();
        if (explicit != null) {
            return explicit;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ROOT;
        }
        if (authentication.getPrincipal() instanceof User user) {
            if (user.getTenantId() == null) {
                throw new IllegalStateException("Utilisateur connecté sans tenant : " + user.getId());
            }
            return user.getTenantId();
        }
        throw new IllegalStateException("Authentification sans utilisateur Parkyn : tenant inconnu");
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT.equals(tenantId);
    }

    // Les sessions sont ouvertes par Spring, pas par getCurrentSession() d'Hibernate : rien à revalider
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
