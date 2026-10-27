package com.example.minishop.web;

import com.example.minishop.domain.Customer;
import com.example.minishop.domain.RefreshToken;
import com.example.minishop.service.AuthService;
import com.example.minishop.service.JwtService;
import com.example.minishop.service.RefreshTokenService;
import com.example.minishop.web.dto.LoginRequest;
import com.example.minishop.web.dto.RefreshRequest;
import com.example.minishop.web.dto.TokenPair;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@Tag(name = "Auth", description = "Login, refresh, logout.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          RefreshTokenService refreshService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
    }

    @Operation(summary = "Login",
            description = "Returns a short-lived access token and a long-lived refresh token.")
    @ApiResponse(responseCode = "200", description = "Tokens issued")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest req) {
        Customer user = authService.verifyCredentials(req.email(), req.password());
        String access = jwtService.sign(user, ACCESS_TTL);
        String refresh = refreshService.issue(user.getId(), null, req.deviceId());
        return ResponseEntity.ok(TokenPair.of(access, refresh, ACCESS_TTL.toSeconds()));
    }

    @Operation(summary = "Refresh",
            description = "Exchanges a valid refresh token for a new access + refresh pair. The old refresh token is revoked.")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(responseCode = "401", description = "Refresh token invalid, expired, or revoked")
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshRequest req) {
        RefreshToken stored = refreshService.lookup(req.refreshToken())
                .orElseThrow(AuthService.InvalidCredentials::new);

        if (stored.isRevoked()) {
            refreshService.revokeFamily(stored.getFamilyId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (stored.isExpired()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID familyId = stored.getFamilyId();
        Long customerId = stored.getCustomerId();
        String deviceId = stored.getDeviceId();

        refreshService.revoke(stored);
        String nextRefresh = refreshService.issue(customerId, familyId, deviceId);

        Customer user = authService.findForSigning(customerId);
        String nextAccess = jwtService.sign(user, ACCESS_TTL);

        return ResponseEntity.ok(TokenPair.of(nextAccess, nextRefresh, ACCESS_TTL.toSeconds()));
    }

    @Operation(summary = "Logout", description = "Revokes the whole refresh-token family.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        refreshService.lookup(req.refreshToken())
                .ifPresent(t -> refreshService.revokeFamily(t.getFamilyId()));
        return ResponseEntity.noContent().build();
    }
}