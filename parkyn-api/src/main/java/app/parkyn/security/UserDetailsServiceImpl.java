package app.parkyn.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import app.parkyn.model.User;
import app.parkyn.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        System.out.println("Loading user: " + email);
        return userRepository.findWithRolesAndPermissionsByEmail(User.normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
