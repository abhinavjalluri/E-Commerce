package com.example.orderservice.mapper;

import com.example.orderservice.dtos.OrderCreateDto;
import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.models.Order;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
  componentModel = MappingConstants.ComponentModel.SPRING)
public interface IOrderMapper {

  Order toEntity(OrderCreateDto orderCreateDto, Long userId);

  OrderDto toDto(Order order);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  Order partialUpdate(OrderDto orderDto, @MappingTarget Order order);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  Order partialUpdate(OrderCreateDto orderCreateDto, @MappingTarget Order order);

}