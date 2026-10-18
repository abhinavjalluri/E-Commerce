package com.example.notificationservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedEvent(
  Long id,
  Long userId,
  List<OrderProduct> products,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
  public record OrderProduct(
    Long productId,
    Integer quantity,
    BigDecimal price
  ) {
  }
}
