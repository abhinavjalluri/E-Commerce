package com.example.authservice.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

  private static final String PREFIX = "login-attempts:";
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final StringRedisTemplate redis;

  public boolean isLocked(String username) {
    try {
      String v = redis.opsForValue().get(PREFIX + norm(username));
      return v != null && Integer.parseInt(v) >= MAX_ATTEMPTS;
    } catch (Exception e) {
      log.warn("Login-attempt check failed, allowing", e);
      return false;
    }
  }

  public void recordFailure(String username) {
    try {
      String key = PREFIX + norm(username);
      redis.opsForValue().increment(key);  // atomic
      redis.expire(key, WINDOW);           // always set, so the key can never live forever
    } catch (Exception e) {
      log.warn("Could not record failed login", e);
    }
  }

  public void reset(String username) {
    try {
      redis.delete(PREFIX + norm(username));
    } catch (Exception e) {
      log.warn("Could not reset login attempts", e);
    }
  }

  private String norm(String username) {
    return username == null ? "" : username.trim().toLowerCase();
  }
}