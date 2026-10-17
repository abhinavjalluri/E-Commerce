package com.example.orderservice.services;

import com.example.orderservice.config.context.ContextHolder;
import com.example.orderservice.exceptions.OrderException;
import com.example.orderservice.dtos.*;
import com.example.orderservice.kafka.IOrderEventProducer;
import com.example.orderservice.mapper.IOrderMapper;
import com.example.orderservice.models.Order;
import com.example.orderservice.models.OrderProduct;
import com.example.orderservice.outbound.http.IProductServiceClient;
import com.example.orderservice.repository.IOrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService implements IOrderService {

  private final IOrderRepository orderRepository;
  private final IOrderMapper orderMapper;
  private final IProductServiceClient productServiceClient;
  private final IOrderEventProducer orderEventProducer;
  private final ContextHolder contextHolder;

  private static Function<OrderProductCreateDto, OrderProduct> orderProductCreateDtoToOrderProduct(Map<Long, ProductDto> productById, Order order) {
    return orderProductCreateDto -> {
      var productDto = Optional.ofNullable(productById.get(orderProductCreateDto.productId()))
        .orElseThrow(() -> new OrderException(OrderException.PRODUCT_DOES_NOT_EXIST));

      if (orderProductCreateDto.quantity() > productDto.quantity()) {
        throw new OrderException(OrderException.STOCK_NOT_AVAILABLE);
      }

      return OrderProduct.builder()
        .price(productDto.price())
        .productId(orderProductCreateDto.productId())
        .quantity(orderProductCreateDto.quantity())
        .order(order)
        .build();
    };
  }

  @Transactional
  @Override
  public OrderDto addOne(OrderCreateDto orderCreateDto) {
    var order = new Order();
    var productsOrder = getOrderProducts(orderCreateDto, order);
    order.setUserId(contextHolder.getUserId());
    order.setProducts(productsOrder);
    var orderSaved = orderRepository.save(order);
    var hasStockUpdated = productServiceClient.updateStock(orderCreateDto.products());
    if (!hasStockUpdated) {
      throw new OrderException(OrderException.ERROR_UPDATE_STOCK);
    }
    var dto = orderMapper.toDto(orderSaved);
    orderEventProducer.sendOrderCreate(dto);
    return dto;
  }

  @Override
  public OrderDto getOne(Long orderId) {
    var order = orderRepository.findById(orderId)
      .orElseThrow(() -> new OrderException(OrderException.ORDER_DOES_NOT_EXIST));
    return orderMapper.toDto(order);
  }

  private List<OrderProduct> getOrderProducts(OrderCreateDto orderCreateDto, Order order) {
    var productsId = orderCreateDto.products()
      .stream()
      .map(OrderProductCreateDto::productId)
      .toList();
    var productById = productServiceClient.getProductById(productsId);
    return orderCreateDto.products()
      .stream()
      .map(orderProductCreateDtoToOrderProduct(productById, order))
      .toList();
  }
}
