package app.parkyn.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import app.parkyn.repository.UserRepository;
import app.parkyn.security.authorization.AccessDecision;
import app.parkyn.security.authorization.UserAccessPolicy;
import app.parkyn.security.authorization.UserAction;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserResponse;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.User;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccessPolicy userAccessPolicy;
    private final RoleAssignmentService roleAssignmentService;


    @Transactional
    public Page<UserResponse> getUsers(User.Role role, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            query.distinct(true);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (role != null) {
                var roles = root.join("roles");
                predicates.add(cb.equal(roles.get("name"), role.name()));
                predicates.add(cb.isNull(roles.get("tenantId")));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }


    @Transactional
    public UserResponse addUser(User currentUser, UserRequest request) {
        enforce(userAccessPolicy.decideRoleAssignment(currentUser, request.role()));

        if (userRepository.existsByEmailInAnyTenant(User.normalizeEmail(request.email()))) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Email already in use");
        }

        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastname());
        user.setEmail(request.email());
        user.setHashedPwd(passwordEncoder.encode(request.password()));
        roleAssignmentService.assign(user, request.role());
        user.setActive(request.active());
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse getUserById(User currentUser, UUID id) {
        User target = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        enforce(userAccessPolicy.decide(currentUser, UserAction.READ, target));

        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse updateUserById(User currentUser, UUID id, UserUpdateRequest request) {

        User user = findActiveUser(id);
        enforce(userAccessPolicy.decide(currentUser, UserAction.UPDATE, user));
        enforce(userAccessPolicy.decideRoleAssignment(currentUser, request.role()));

        if (request.firstname() != null) user.setFirstName(request.firstname());
        if (request.lastname()  != null) user.setLastName(request.lastname());
        if (request.email()     != null) user.setEmail(request.email());
        if (request.password()  != null) user.setHashedPwd(passwordEncoder.encode(request.password()));
        if (request.role() != null) roleAssignmentService.assign(user, request.role());
        if (request.active()    != null) user.setActive(request.active());
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUserById(User currentUser, UUID id) {
        User user = findActiveUser(id);
        enforce(userAccessPolicy.decide(currentUser, UserAction.DELETE, user));

        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public Page<UserResponse> getDrivers(String search, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            query.distinct(true);
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.isTrue(root.get("active")));
            var roles = root.join("roles");
            predicates.add(cb.equal(roles.get("name"), User.Role.DRIVER.name()));
            predicates.add(cb.isNull(roles.get("tenantId")));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }


    @Transactional
    public UserResponse reactivateUserById(User currentUser, UUID id) {
        User user = userRepository.findById(id)
            .filter(v -> !v.isActive())
            .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Inactive user not found"));
        enforce(userAccessPolicy.decide(currentUser, UserAction.REACTIVATE, user));
        user.setActive(true);
        return UserResponse.from(userRepository.save(user));
    }

    private User findActiveUser(UUID id){
        return userRepository.findById(id).filter(v -> v.isActive()).orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "User not found"));
    }

    private void enforce(AccessDecision decision) {
        switch (decision) {
            case AccessDecision.Granted g -> { }
            case AccessDecision.Denied d -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, d.reason());
            case AccessDecision.NotFound n -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable");
        }
    }
}
