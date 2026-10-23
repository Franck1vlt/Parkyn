package app.parkyn.security.authorization;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import app.parkyn.model.User;
import app.parkyn.model.Reservation;
import app.parkyn.service.PermissionService;
import lombok.RequiredArgsConstructor;

/**
 * Portée des réservations (ADR-0002) : sur quelles réservations un utilisateur peut faire une action.
 * Renvoie une condition ajoutée à la requête SQL, jamais un filtre en mémoire.
 * Permissions reservation:<action>:all (tout le tenant) ou reservation:<action>:own (ses réservations) ;
 * « all inclut own ».
 */
@Component
@RequiredArgsConstructor
public class ReservationScope {

    public enum Action {
        READ("read"), UPDATE("update"), CANCEL("cancel");

        private final String code;

        Action(String code) {
            this.code = code;
        }
    }

    private final PermissionService permissionService;

    public Specification<Reservation> readableBy(User user) {
        return allowedFor(user, Action.READ);
    }

    public Specification<Reservation> allowedFor(User user, Action action) {
        if (coversAll(user, action)) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (permissionService.permissionCodesOf(user.getId()).contains("reservation:" + action.code + ":own")) {
            return (root, query, cb) -> cb.equal(root.get("driver").get("id"), user.getId());
        }
        return (root, query, cb) -> cb.disjunction();
    }

    // Portée « all » : toutes les réservations du tenant (AGENT, ADMIN), et non seulement les siennes
    public boolean coversAll(User user, Action action) {
        return permissionService.permissionCodesOf(user.getId()).contains("reservation:" + action.code + ":all");
    }

    public boolean canCreateForAnyDriver(User user) {
        Set<String> permissions = permissionService.permissionCodesOf(user.getId());
        return permissions.contains("reservation:create:all");
    }
}
