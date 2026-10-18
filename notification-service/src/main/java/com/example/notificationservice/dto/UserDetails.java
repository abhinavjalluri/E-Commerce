package com.example.notificationservice.dto;

public record UserDetails(
  Long userId,
  String name,
  String email
) {
}
