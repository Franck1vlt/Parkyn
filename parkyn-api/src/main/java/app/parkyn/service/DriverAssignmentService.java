package app.parkyn.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import app.parkyn.dto.ReservationRequest;
import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverAssignmentService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleAssignmentService roleAssignmentService;

    public User getOrCreateDriver(ReservationRequest request) {
        // Cas 1 : driverId fourni -> chercher le driver existant
        if (request.driverId() != null){
            return userRepository.findById(request.driverId())
                .filter(u -> u.getRole() == User.Role.DRIVER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        }

        // Cas 2 : pas de driverId -> créer le driver
        if (request.driverFirstName() == null || request.driverLastName() == null || request.driverEmail() == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driverFirstName, driverLasName and driverEmail are required to create a new driver");
        }

        // Email déjà utilisé ?
        if (userRepository.existsByEmailInAnyTenant(User.normalizeEmail(request.driverEmail()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User driver = new User();
        driver.setFirstName(request.driverFirstName());
        driver.setLastName(request.driverLastName());
        driver.setEmail(request.driverEmail());
        driver.setHashedPwd(passwordEncoder.encode(UUID.randomUUID().toString()));
        roleAssignmentService.assign(driver, User.Role.DRIVER);
        driver.setActive(true);
        return userRepository.saveAndFlush(driver);
    }

}
