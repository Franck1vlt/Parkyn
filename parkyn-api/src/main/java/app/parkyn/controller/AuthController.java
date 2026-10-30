package app.parkyn.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.LoginRequest;
import app.parkyn.dto.LoginResponse;
import app.parkyn.dto.MeResponse;
import app.parkyn.dto.RegisterRequest;
import app.parkyn.dto.ResendVerificationRequest;
import app.parkyn.dto.VerifyEmailRequest;
import app.parkyn.model.User;
import app.parkyn.security.PublicEndpoint;
import app.parkyn.security.RefreshTokenCookie;
import app.parkyn.service.AuthService;
import app.parkyn.service.AuthTokens;
import app.parkyn.service.VerificationTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final VerificationTokenService verificationTokenService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PublicEndpoint 
    @PostMapping("/login") 
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request){
        AuthTokens tokens = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()).toString())
                .body(tokens.body());
    }

    @PublicEndpoint 
    @PostMapping("/refresh") 
    public ResponseEntity<LoginResponse> refreshToken(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken
        ) {
        AuthTokens tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()).toString())
                .body(tokens.body());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal User user) {
        authService.logout(user);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
                .build();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.me(user));
    }

    @PublicEndpoint 
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.createAccount(request);
        return ResponseEntity.status(202).build();
    }

    @PublicEndpoint
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verificationTokenService.verify(request.token());
        return ResponseEntity.ok().build();
    }

    @PublicEndpoint 
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }
    
}
