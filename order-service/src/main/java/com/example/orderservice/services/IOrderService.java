package com.example.orderservice.services;

import com.example.orderservice.dtos.OrderCreateDto;
import com.example.orderservice.dtos.OrderDto;

import java.util.List;

public interface IOrderService {

  OrderDto addOne(OrderCreateDto orderCreateDto);

  OrderDto getOne(Long orderId);

  List<OrderDto> getAll();

  OrderDto updateOne(Long orderId, OrderCreateDto orderCreateDto);

  void deleteOne(Long orderId);

}
