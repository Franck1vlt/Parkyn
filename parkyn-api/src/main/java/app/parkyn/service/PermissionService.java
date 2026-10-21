package app.parkyn.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import app.parkyn.repository.RoleRepository;
import app.parkyn.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class PermissionService {

    private final UserRepository userRepository;
    public static final String CACHE_NAME = "userPermissions";

    @Cacheable(cacheNames = CACHE_NAME, key = "#userId")
    public Set<String> permissionCodesOf(UUID userId) {
        return userRepository.findPermissionCodesByUserId(userId);
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "#userId")
    public void evict(UUID userId) {}

}
