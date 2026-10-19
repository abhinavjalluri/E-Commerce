package com.example.apigateway;

import com.example.apigateway.filters.AuthenticationPrefilter;
import com.example.apigateway.filters.SwaggerFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
@OpenAPIDefinition(info = @Info(title = "E-Commerce-Shop API"))
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }

  private static GatewayFilterSpec limit(GatewayFilterSpec f, RedisRateLimiter limiter, KeyResolver resolver) {
    return f.requestRateLimiter(c -> {
      c.setRateLimiter(limiter);
      c.setKeyResolver(resolver);
    });
  }

  @Bean
  public RouteLocator myRoutes(
    RouteLocatorBuilder builder,
    AuthenticationPrefilter authFilter,
    SwaggerFilter swaggerFilter,
    @Qualifier("strictRateLimiter") RedisRateLimiter strict,
    @Qualifier("publicRateLimiter") RedisRateLimiter pub,
    @Qualifier("userRateLimiter") RedisRateLimiter user,
    @Qualifier("ipKeyResolver") KeyResolver ip,
    @Qualifier("userKeyResolver") KeyResolver userKey
  ) {
    return builder.routes()
      // ---------- auth ----------
      .route("auth-service", r -> r.path("/api/v1/auth/login")
        .filters(f -> limit(f, strict, ip))
        .uri("lb://auth-service"))
      .route("auth-service-logout", r -> r.path("/api/v1/auth/logout")
        .and().method(HttpMethod.POST)
        .filters(f -> limit(f.filter(authFilter), user, userKey))
        .uri("lb://auth-service"))

      // ---------- users ----------
      .route("user-service-create", r -> r.path("/api/v1/users")
        .and().method(HttpMethod.POST)
        .filters(f -> limit(f, strict, ip))
        .uri("lb://user-service"))
      .route("user-service-get-all-public", r -> r.path("/api/v1/users")
        .and().method(HttpMethod.GET)
        .filters(f -> limit(f, pub, ip))
        .uri("lb://user-service"))
      .route("user-service-cache-refresh-public", r -> r.path("/api/v1/users/cache/refresh")
        .and().method(HttpMethod.POST)
        .filters(f -> limit(f, strict, ip))
        .uri("lb://user-service"))
      .route("user-service-with-auth", r -> r.path("/api/v1/users", "/api/v1/users/**")
        .filters(f -> limit(f.filter(authFilter), user, userKey))
        .uri("lb://user-service"))

      // ---------- inventory ----------
      .route("inventory-service-with-auth", r -> r.path("/api/v1/products/users/me/**")
        .filters(f -> limit(f.filter(authFilter), user, userKey))
        .uri("lb://inventory-service"))
      .route("inventory-service-no-auth", r -> r.path("/api/v1/products/**")
        .filters(f -> limit(f, pub, ip))
        .uri("lb://inventory-service"))

      // ---------- reviews ----------
      .route("reviews-service-with-auth", r -> r.path("/api/v1/reviews/users/me/**")
        .filters(f -> limit(f.filter(authFilter), user, userKey))
        .uri("lb://reviews-service"))
      .route("reviews-service-no-auth", r -> r.path("/api/v1/reviews/**")
        .filters(f -> limit(f, pub, ip))
        .uri("lb://reviews-service"))

      // ---------- orders ----------
      .route("orders-service-with-auth", r -> r.path("/api/v1/orders/users/me/**")
        .filters(f -> limit(f.filter(authFilter), user, userKey))
        .uri("lb://order-service"))
      .route("orders-service-no-auth", r -> r.path("/api/v1/orders/**")
        .filters(f -> limit(f, pub, ip))
        .uri("lb://order-service"))

      // ---------- swagger docs (unchanged) ----------
      .route("user-service-api-docs", r -> r.path("/user-service/api-docs")
        .filters(f -> f.filter(swaggerFilter.apply(new SwaggerFilter.Config())))
        .uri("lb://user-service"))
      .route("inventory-service-api-docs", r -> r.path("/inventory-service/api-docs")
        .filters(f -> f.filter(swaggerFilter.apply(new SwaggerFilter.Config())))
        .uri("lb://inventory-service"))
      .route("order-service-api-docs", r -> r.path("/order-service/api-docs")
        .filters(f -> f.filter(swaggerFilter.apply(new SwaggerFilter.Config())))
        .uri("lb://order-service"))
      .route("reviews-service-api-docs", r -> r.path("/reviews-service/api-docs")
        .filters(f -> f.filter(swaggerFilter.apply(new SwaggerFilter.Config())))
        .uri("lb://reviews-service"))
      .route("auth-service-api-docs", r -> r.path("/auth-service/api-docs")
        .filters(f -> f.filter(swaggerFilter.apply(new SwaggerFilter.Config())))
        .uri("lb://auth-service"))
      .build();
  }
}
