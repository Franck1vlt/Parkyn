package app.parkyn.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        // findByEmail charge aussi les rôles (@EntityGraph) : getRole() fonctionne hors transaction
        return userRepository.findByEmail(User.normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    // Appelé par Spring Security après une connexion réussie quand le hash stocké n'est pas
    // dans le format courant (ancien BCrypt) : enregistre le nouveau hash Argon2id.
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newEncodedPassword) {
        User stored = userRepository.findByEmail(User.normalizeEmail(user.getUsername()))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + user.getUsername()));
        stored.setHashedPwd(newEncodedPassword);
        return userRepository.save(stored);
    }
}
