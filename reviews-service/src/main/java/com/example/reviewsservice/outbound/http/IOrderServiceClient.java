package com.example.reviewsservice.outbound.http;

import com.example.reviewsservice.dtos.OrderDto;

import java.util.Optional;

public interface IOrderServiceClient {

  Optional<OrderDto> getOrder(Long orderId);

}
