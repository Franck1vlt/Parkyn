package app.parkyn.service;

import org.springframework.stereotype.Service;

import app.parkyn.model.User;
import app.parkyn.repository.RoleRepository;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class RoleAssignmentService {

    private final PermissionService permissionService;
    private final RoleRepository roleRepository;
 
    public void assign(User user, User.Role role) {
        var systemRole = roleRepository.findByNameAndTenantIdIsNull(role.name())
            .orElseThrow(() -> new IllegalStateException("Role système introuvable : " + role.name()));
        user.getRoles().clear();
        user.getRoles().add(systemRole);

        if (user.getId() != null) {
            permissionService.evict(user.getId());
        }
    }
}
