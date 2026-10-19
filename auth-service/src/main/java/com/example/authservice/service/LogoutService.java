package com.example.authservice.service;

import com.example.authservice.jwt.JWTHelper;
import com.example.authservice.redis.TokenRevocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

  private final JWTHelper jwtHelper;
  private final TokenRevocationService revocationService;

  public void logout(String jwt) {
    jwtHelper.parseClaims(jwt).ifPresent(c -> {
      if (c.getId() != null && c.getExpiration() != null) {
        revocationService.revoke(c.getId(), c.getExpiration().toInstant());
      }
    });
  }
}