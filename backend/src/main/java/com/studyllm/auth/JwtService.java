package com.studyllm.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and validates the HMAC-signed JWTs used as bearer tokens for authenticated requests. */
@Service
public class JwtService {

  private final Key key;
  private final Duration expiration;

  public JwtService(
      @Value("${studyllm.jwt.secret}") String secret,
      @Value("${studyllm.jwt.expiration-minutes}") long expirationMinutes) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("STUDYLLM_JWT_SECRET must be set");
    }
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expiration = Duration.ofMinutes(expirationMinutes);
  }

  /** Builds a signed token with the user ID as subject, expiring after the configured duration. */
  public String issueToken(UUID userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiration)))
        .signWith(key)
        .compact();
  }

  /** Verifies signature and expiry, returning the subject as a user ID, or empty if invalid/expired. */
  public Optional<UUID> validateAndGetUserId(String token) {
    try {
      String subject = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();
      return Optional.of(UUID.fromString(subject));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
