package app.parkyn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;

@RepositoryTest
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    private User saveUser(String email) {
        User user = new User();
        user.setFirstName("Jean");
        user.setLastName("Dupont");
        user.setEmail(email);
        user.setHashedPwd("hash");
        return userRepository.saveAndFlush(user);
    }

    @Test
    void emailIsStoredLowercase() {
        // Vérifie que l'email stocké est normalisé en minuscules.
        User savedUser = saveUser("Jean@parkyn.app");
        assertEquals("jean@parkyn.app", savedUser.getEmail());
        assertTrue(userRepository.findByEmail("jean@parkyn.app").isPresent());
    }
}
