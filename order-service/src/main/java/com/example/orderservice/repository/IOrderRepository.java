package com.example.orderservice.repository;

import com.example.orderservice.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IOrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByUserId(Long userId);

  Optional<Order> findByIdAndUserId(Long id, Long userId);

  @Modifying
  @Query("delete from order_products p where p.order.id = ?1")
  void deleteProductsByOrderId(Long orderId);

}
