package app.parkyn.controller;

import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.UserRequest;
import app.parkyn.dto.UserResponse;
import app.parkyn.dto.UserUpdateRequest;
import app.parkyn.model.User;
import app.parkyn.service.UserService;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasAnyRole('ADMIN')")
    @GetMapping("")
    public ResponseEntity<Page<UserResponse>> getUsers(
        @RequestParam(required = false) User.Role role,
        @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok().body(userService.getUsers(role, pageable));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PostMapping("")
    public ResponseEntity<UserResponse> addUser(
        @AuthenticationPrincipal User currentUser,
        @Valid @RequestBody UserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.addUser(currentUser, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok().body(userService.getUserById(currentUser, id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUserById(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        UserResponse response = userService.updateUserById(currentUser, id, request);
        return ResponseEntity.ok().body(response);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id
    ) {
        userService.deleteUserById(currentUser, id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<UserResponse> reactivateUser(
        @AuthenticationPrincipal User currentUser,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok().body(userService.reactivateUserById(currentUser, id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("/drivers")
    public ResponseEntity<Page<UserResponse>> getDrivers(
        @RequestParam(required = false) String search,
        @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable
    ) {
        return ResponseEntity.ok().body(userService.getDrivers(search, pageable));
    }
}
