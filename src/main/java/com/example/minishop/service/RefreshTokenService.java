package com.example.minishop.service;

import com.example.minishop.domain.RefreshToken;
import com.example.minishop.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public String issue(long customerId, UUID familyId, String deviceId) {
        String raw = generateRawToken();
        repo.save(new RefreshToken(
                sha256Hex(raw),
                familyId == null ? UUID.randomUUID() : familyId,
                customerId,
                deviceId,
                Instant.now().plus(TTL)));
        return raw;
    }

    public Optional<RefreshToken> lookup(String rawToken) {
        return repo.findByTokenHash(sha256Hex(rawToken));
    }

    @Transactional
    public void revoke(RefreshToken token) {
        token.revoke();
        repo.save(token);
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        List<RefreshToken> family = repo.findByFamilyId(familyId);
        for (RefreshToken t : family) {
            if (!t.isRevoked()) {
                t.revoke();
                repo.save(t);
            }
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}