package com.example.userservice.dto;

public record UserCacheDto(
  Long userId,
  String name,
  String email
) {
}
