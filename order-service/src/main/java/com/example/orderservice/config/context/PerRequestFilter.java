package com.example.orderservice.config.context;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.example.orderservice.config.context.ContextHolder.CORRELATION_ID;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class PerRequestFilter
  extends OncePerRequestFilter {

  private final ContextHolder contextHolder;

  private static Long parseUserId(HttpServletRequest request) {
    try {
      var userIdString = request.getHeader("userId");
      return Long.parseLong(userIdString);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    var correlationId = request.getHeader(CORRELATION_ID);
    var userId = parseUserId(request);
    var username = request.getHeader("username");
    var context = new ContextData(correlationId, userId, username);
    contextHolder.set(context);
    try {
      filterChain.doFilter(request, response);
    } finally {
      contextHolder.remove();
    }
  }
}
