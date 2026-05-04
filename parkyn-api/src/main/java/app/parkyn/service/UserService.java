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


    @Transactional
    public Page<UserResponse> getUsers(User.Role role, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return userRepository.findAll(spec, pageable).map(UserResponse::from);
    }


    @Transactional
    public UserResponse addUser(User currentUser, UserRequest request) {
        if (currentUser.getRole() == User.Role.AGENT && request.role() != User.Role.DRIVER) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                "Agents can only create DRIVER accounts");
        }

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409), "Email already in use");
        }

        User user = new User();
        user.setFirstName(request.firstname());
        user.setLastName(request.lastname());
        user.setEmail(request.email());
        user.setHashedPwd(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(request.active());
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        return UserResponse.from(user);
    }


    @Transactional
    public UserResponse updateUserById(UUID id, UserUpdateRequest request) {
        User user = findActiveUser(id);
        if (request.firstname() != null) user.setFirstName(request.firstname());
        if (request.lastname()  != null) user.setLastName(request.lastname());
        if (request.email()     != null) user.setEmail(request.email());
        if (request.password()  != null) user.setHashedPwd(passwordEncoder.encode(request.password()));
        if (request.role()      != null) user.setRole(request.role());
        if (request.active()    != null) user.setActive(request.active());
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUserById(User currentUser, UUID id) {
        User user = findActiveUser(id);

        if (currentUser.getRole() == User.Role.AGENT && user.getRole() != User.Role.DRIVER) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                "Agents can only delete DRIVER accounts");
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public Page<UserResponse> getDrivers(String search, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.equal(root.get("role"), User.Role.DRIVER));
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
    public UserResponse reactivateUserById(UUID id) {
        User user = userRepository.findById(id)
            .filter(v -> !v.isActive())
            .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Inactive user not found"));
        user.setActive(true);
        return UserResponse.from(userRepository.save(user));
    }

    private User findActiveUser(UUID id){
        return userRepository.findById(id).filter(v -> v.isActive()).orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "User not found"));
    }

}
