package com.example.authservice.controller;

import com.example.authservice.config.context.ContextHolder;
import com.example.authservice.dtos.LoginDto;
import com.example.authservice.exceptions.AuthException;
import com.example.authservice.redis.LoginAttemptService;
import com.example.authservice.service.IAuthService;
import com.example.authservice.service.LogoutService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping(value = {"api/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

  private final IAuthService authService;
  private final LogoutService logoutService;
  private final LoginAttemptService loginAttemptService;
  private final ContextHolder contextHolder;

  @Operation(
    summary = "Performs the login",
    responses = {
      @ApiResponse(responseCode = "204"),
      @ApiResponse(responseCode = "429", description = "Too many failed attempts")
    }
  )
  @PostMapping("/login")
  public ResponseEntity<Object> login(HttpServletRequest request, @Valid @RequestBody LoginDto loginDto) {
    logRequest(request, null); // never log the login body: it contains the password

    var username = loginDto.username();
    if (loginAttemptService.isLocked(username)) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again later.");
    }

    String jwt;
    try {
      jwt = authService.login(loginDto);
    } catch (AuthException e) {
      loginAttemptService.recordFailure(username); // only real login failures count, not outages
      throw e;
    }
    loginAttemptService.reset(username);

    var responseHeaders = new HttpHeaders();
    responseHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
    return ResponseEntity.noContent().headers(responseHeaders).build();
  }

  @Operation(
    summary = "Logout: revokes the current JWT",
    responses = {@ApiResponse(responseCode = "204")},
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request,
                                     @RequestHeader(HttpHeaders.AUTHORIZATION) String headerAuthorization) {
    logRequest(request, null);
    logoutService.logout(headerAuthorization.replace("Bearer ", ""));
    return ResponseEntity.noContent().build();
  }

  @Hidden
  @PostMapping("/validate")
  public ResponseEntity<Object> validateToken(HttpServletRequest request, @RequestHeader(HttpHeaders.AUTHORIZATION) String headerAuthorization) {
    logRequest(request, null);
    var jwt = headerAuthorization.replace("Bearer ", "");
    var userHeader = authService.validateToken(jwt);
    var responseHeaders = new HttpHeaders();
    responseHeaders.set("userId", userHeader.id().toString());
    responseHeaders.set("username", userHeader.username());
    return ResponseEntity.ok().headers(responseHeaders).build();
  }

  private void logRequest(HttpServletRequest request, Object obj) {
    log.info("{} - {} - {} - {} - {}", request.getMethod(), request.getRequestURI(),
      contextHolder.getCorrelationId(), contextHolder.getUsername(), obj);
  }
}