package com.example.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

  /** Who is the caller? By client IP. */
  @Bean
  @Primary
  public KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(clientIp(exchange));
  }

  /** By user id (set by AuthenticationPrefilter, which must run first); falls back to IP. */
  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> {
      String userId = exchange.getRequest().getHeaders().getFirst("userId");
      return Mono.just(userId != null ? "user:" + userId : "ip:" + clientIp(exchange));
    };
  }

  // RedisRateLimiter(replenishRate per second, burstCapacity, requestedTokens per request)

  /** Login and signup: each request costs 5 tokens, refill 1/s, burst 10 => 2 immediate, then 1 every 5s. */
  @Bean
  public RedisRateLimiter strictRateLimiter() {
    return new RedisRateLimiter(1, 10, 5);
  }

  /** Anonymous traffic: 20 req/s sustained, bursts of 40. */
  @Bean
  @Primary
  public RedisRateLimiter publicRateLimiter() {
    return new RedisRateLimiter(20, 40, 1);
  }

  /** Authenticated users: 10 req/s sustained, bursts of 20. */
  @Bean
  public RedisRateLimiter userRateLimiter() {
    return new RedisRateLimiter(10, 20, 1);
  }

  private static String clientIp(ServerWebExchange exchange) {
    var addr = exchange.getRequest().getRemoteAddress();
    return addr == null ? "unknown" : addr.getAddress().getHostAddress();
  }
}