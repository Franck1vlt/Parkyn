package app.parkyn.security.authorization;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import app.parkyn.model.User;
import app.parkyn.model.Reservation;
import app.parkyn.service.PermissionService;
import lombok.RequiredArgsConstructor;

/**
 * Portée des réservations (ADR-0002) : quelles réservations un utilisateur peut voir.
 * Renvoie une condition ajoutée à la requête SQL, jamais un filtre en mémoire.
 */
@Component 
@RequiredArgsConstructor 
public class ReservationScope {

    private final PermissionService permissionService;

    public Specification<Reservation> readableBy(User user) {
        Set<String> permissions = permissionService.permissionCodesOf(user.getId());

        if (permissions.contains("reservation:read:all")) {
            return (root, query, cb) -> cb.conjunction();
        }

        else if (permissions.contains("reservation:read:own")) {
            return (root, query, cb) -> cb.equal(root.get("driver").get("id"), user.getId());
        }

        else {
            return (root, query, cb) -> cb.disjunction();
        }
    }
}
