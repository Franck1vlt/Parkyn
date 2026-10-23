package app.parkyn.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import app.parkyn.model.User;

/**
 * Le résolveur décide quelles données chaque session Hibernate peut voir.
 * Tout cas imprévu doit échouer, jamais retomber sur « tous les tenants ».
 */
class CurrentTenantResolverTest {

    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final CurrentTenantResolver resolver = new CurrentTenantResolver();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedUserGetsTheirTenant() {
        authenticate(userOf(TENANT_B));

        UUID tenant = resolver.resolveCurrentTenantIdentifier();

        assertEquals(TENANT_B, tenant);
        assertFalse(resolver.isRoot(tenant));
    }

    // Connexion, inscription, traitement planifié : aucun tenant connu, le filtre est désactivé
    @Test
    void noAuthenticationIsRoot() {
        assertTrue(resolver.isRoot(resolver.resolveCurrentTenantIdentifier()));
    }

    // Requête publique : Spring Security pose un utilisateur anonyme
    @Test
    void anonymousIsRoot() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertTrue(resolver.isRoot(resolver.resolveCurrentTenantIdentifier()));
    }

    @Test
    void defaultTenantIsNotRoot() {
        assertFalse(resolver.isRoot(Tenants.DEFAULT_ID));
    }

    @Test
    void explicitTenantWinsOverRoot() {
        TenantContext.runAs(TENANT_B, () ->
            assertEquals(TENANT_B, resolver.resolveCurrentTenantIdentifier()));

        assertTrue(resolver.isRoot(resolver.resolveCurrentTenantIdentifier()), "Le tenant explicite doit être retiré après");
    }

    // Imbriqué : à la sortie du runAs intérieur, le tenant extérieur est rétabli (pas simplement retiré)
    @Test
    void nestedExplicitTenantRestoresOuterOne() {
        TenantContext.runAs(Tenants.DEFAULT_ID, () -> {
            TenantContext.runAs(TENANT_B, () ->
                assertEquals(TENANT_B, resolver.resolveCurrentTenantIdentifier()));
            assertEquals(Tenants.DEFAULT_ID, resolver.resolveCurrentTenantIdentifier());
        });
    }

    @Test
    void explicitTenantIsRemovedEvenOnFailure() {
        assertThrows(IllegalStateException.class, () -> TenantContext.runAs(TENANT_B, () -> {
            throw new IllegalStateException("échec du traitement");
        }));

        assertTrue(resolver.isRoot(resolver.resolveCurrentTenantIdentifier()));
    }

    // Un utilisateur authentifié sans tenant ne doit jamais tout voir
    @Test
    void userWithoutTenantIsRefused() {
        authenticate(userOf(null));

        assertThrows(IllegalStateException.class, resolver::resolveCurrentTenantIdentifier);
    }

    // Authentification d'un autre type (ex. utilisateur de test Spring) : tenant inconnu, refusé
    @Test
    void unknownPrincipalIsRefused() {
        authenticate("someone");

        assertThrows(IllegalStateException.class, resolver::resolveCurrentTenantIdentifier);
    }

    private static User userOf(UUID tenantId) {
        User user = new User();
        user.setEmail("user@b.test");
        user.setTenantId(tenantId);
        return user;
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
