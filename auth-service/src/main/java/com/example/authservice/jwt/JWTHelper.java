package com.example.authservice.jwt;

import com.example.authservice.dtos.UserDto;
import com.example.authservice.dtos.UserHeader;
import com.example.authservice.redis.TokenRevocationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JWTHelper {

  private static final String SECRET_KEY = "LSE1yJq4TJuIneLtE1ZjwkGITMQJrgiFFZr8vOTGCWc=";
  private static final SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "HmacSHA256");

  private final TokenRevocationService revocationService;

  public String generateToken(UserDto user) throws JsonProcessingException {
    var expirationDate = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));

    var claims = Jwts.claims();
    claims.put("username", user.username());
    claims.put("userId", user.id());

    return Jwts.builder()
      .setClaims(claims)
      .setId(UUID.randomUUID().toString())   // jti, needed for revocation
      .setIssuedAt(new Date())
      .setIssuer("auth-service")
      .setAudience("e-commerce-store")
      .setExpiration(expirationDate)
      .signWith(key)
      .compact();
  }

  /** Signature and expiry check only. */
  public Optional<Claims> parseClaims(String token) {
    try {
      return Optional.of(Jwts.parserBuilder().setSigningKey(key).build()
        .parseClaimsJws(token).getBody());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Signature, expiry AND revocation. Used by /validate. */
  public Optional<UserHeader> decodeJWT(String token) {
    try {
      var claims = parseClaims(token);
      if (claims.isEmpty() || revocationService.isRevoked(claims.get().getId())) {
        return Optional.empty();
      }
      var userId = claims.get().get("userId", Long.class);
      var username = claims.get().get("username", String.class);
      return Optional.of(new UserHeader(userId, username));
    } catch (JwtException e) {
      return Optional.empty();
    }
  }
}
