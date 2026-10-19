package com.example.authservice.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

  private static final String PREFIX = "revoked-token:";
  private final StringRedisTemplate redis;

  /** Stored only until the token would expire anyway. Errors propagate: a failed logout must not look successful. */
  public void revoke(String jti, Instant expiresAt) {
    Duration remaining = Duration.between(Instant.now(), expiresAt);
    if (remaining.isNegative() || remaining.isZero()) return;
    redis.opsForValue().set(PREFIX + jti, "1", remaining);
  }

  /** Fails open: if Redis is down, tokens are still validated by signature and expiry. */
  public boolean isRevoked(String jti) {
    if (jti == null) return false; // tokens issued before this change have no jti
    try {
      return redis.hasKey(PREFIX + jti);
    } catch (Exception e) {
      log.warn("Revocation check failed, allowing token", e);
      return false;
    }
  }
}