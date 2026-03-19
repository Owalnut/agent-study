package com.walnut.agent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationDays;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-days}") long expirationDays
    ) {
        String normalized = secret.length() < 32 ? (secret + "00000000000000000000000000000000") : secret;
        this.key = Keys.hmacShaKeyFor(normalized.substring(0, 32).getBytes(StandardCharsets.UTF_8));
        this.expirationDays = expirationDays;
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationDays, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long expiresAtEpochMs() {
        return Instant.now().plus(expirationDays, ChronoUnit.DAYS).toEpochMilli();
    }

    public String parseSubject(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claims.getSubject();
    }
}
