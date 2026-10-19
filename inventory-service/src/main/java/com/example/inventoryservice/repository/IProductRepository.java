package com.example.inventoryservice.repository;

import com.example.inventoryservice.dtos.ProductCacheDto;
import com.example.inventoryservice.models.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IProductRepository extends JpaRepository<Product, Long> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, Long id);

  /** Row locks, ordered by id so concurrent orders lock in the same order (avoids deadlocks). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM product p WHERE p.id IN :ids ORDER BY p.id")
  List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM product p WHERE p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  @Query(
    value = "SELECT new com.example.inventoryservice.dtos.ProductCacheDto(p.id, p.name, p.description) FROM product p ORDER BY p.id",
    countQuery = "SELECT COUNT(p) FROM product p"
  )
  Page<ProductCacheDto> findAllForCache(Pageable pageable);

}