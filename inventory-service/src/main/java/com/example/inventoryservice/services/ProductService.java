package com.example.inventoryservice.services;

import com.example.inventoryservice.dtos.*;
import com.example.inventoryservice.exceptions.ProductException;
import com.example.inventoryservice.mapper.IProductMapper;
import com.example.inventoryservice.models.Product;
import com.example.inventoryservice.repository.IProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {

  private static final Set<String> SORTABLE = Set.of("id", "name", "price", "quantity");
  private static final int MAX_PAGE_SIZE = 100;

  private final IProductMapper productMapper;
  private final IProductRepository productRepository;
  private final ProductCacheService cache;

  // ---------------- writes ----------------

  @Override
  @Transactional
  public ProductDto addOne(ProductCreateDto productCreateDto) {
    if (productRepository.existsByName(productCreateDto.name())) {
      throw new ProductException(ProductException.ALREADY_EXISTS_PRODUCT);
    }
    var saved = productRepository.save(productMapper.toProduct(productCreateDto));
    cache.cacheProductsAfterCommit(List.of(saved), true);
    return productMapper.toDto(saved);
  }

  @Override
  @Transactional
  public ProductDto updateOne(Long id, ProductCreateDto productCreateDto) {
    var product = productRepository.findByIdForUpdate(id)
      .orElseThrow(() -> new ProductException(ProductException.PRODUCT_DOES_NOT_EXIST));
    if (productRepository.existsByNameAndIdNot(productCreateDto.name(), id)) {
      throw new ProductException(ProductException.ALREADY_EXISTS_PRODUCT);
    }
    productMapper.partialUpdate(productCreateDto, product);
    var saved = productRepository.save(product);
    cache.cacheProductsAfterCommit(List.of(saved), true);
    return productMapper.toDto(saved);
  }

  @Override
  @Transactional
  public void deleteOne(Long id) {
    var product = productRepository.findByIdForUpdate(id)
      .orElseThrow(() -> new ProductException(ProductException.PRODUCT_DOES_NOT_EXIST));
    productRepository.delete(product);
    cache.evictProductAfterCommit(id);
  }

  @Override
  @Transactional
  public void increaseStock(List<ProductStockQuantityDto> productsQuantities) {
    adjustStock(productsQuantities, +1);
  }

  @Override
  @Transactional
  public void decreaseStock(List<ProductStockQuantityDto> productsQuantities) {
    adjustStock(productsQuantities, -1);
  }

  /** Locks the rows in the DB, so concurrent orders cannot oversell. Redis is never consulted here. */
  private void adjustStock(List<ProductStockQuantityDto> productsQuantities, int sign) {
    // merge duplicate product ids in the request
    Map<Long, Integer> deltas = productsQuantities.stream().collect(Collectors.toMap(
      ProductStockQuantityDto::productId,
      ProductStockQuantityDto::quantity,
      Integer::sum,
      LinkedHashMap::new));

    var products = productRepository.findAllByIdForUpdate(deltas.keySet());
    if (products.size() != deltas.size()) {
      throw new ProductException(ProductException.PRODUCT_DOES_NOT_EXIST);
    }

    boolean availabilityChanged = false;
    for (Product p : products) {
      int before = p.getQuantity();
      int after = before + sign * deltas.get(p.getId());
      if (after < 0) {
        throw new ProductException(ProductException.QUANTITY_LOWER_ZERO + ", " + p.getName() + ", " + before);
      }
      p.setQuantity(after);
      availabilityChanged |= (before > 0) != (after > 0);
    }

    var saved = productRepository.saveAll(products);
    cache.cacheProductsAfterCommit(saved, availabilityChanged);
  }

  // ---------------- reads (cache-aside) ----------------

  @Override
  public ProductDto getOneById(Long id) {
    var cached = cache.getProduct(id);
    if (cached.isPresent()) {
      return productMapper.toDto(cached.get());
    }
    var product = productRepository.findById(id)
      .orElseThrow(() -> new ProductException(ProductException.PRODUCT_DOES_NOT_EXIST));
    cache.cacheProductIfAbsent(product);
    return productMapper.toDto(product);
  }

  @Override
  public List<ProductDto> getAllByIds(List<Long> ids) {
    List<Long> distinct = ids.stream().distinct().toList();
    Map<Long, Product> found = cache.getProducts(distinct);

    List<Long> missing = distinct.stream().filter(id -> !found.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      for (Product p : productRepository.findAllById(missing)) {
        found.put(p.getId(), p);
        cache.cacheProductIfAbsent(p);
      }
    }

    List<Product> ordered = distinct.stream().map(found::get).filter(Objects::nonNull).toList();
    return productMapper.toDto(ordered);
  }

  @Override
  public List<ProductDto> getAll() {
    String key = cache.catalogKey("all"); // read the version first, then the DB
    var cached = cache.getJson(key, new TypeReference<List<ProductDto>>() {});
    if (cached.isPresent()) {
      return cached.get();
    }
    var result = productMapper.toDto(productRepository.findAll());
    cache.putJson(key, result);
    return result;
  }

  @Override
  public PageResponse<ProductDto> getPage(int page, int size, String sortBy, String direction) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    String safeSort = SORTABLE.contains(sortBy) ? sortBy : "id";
    Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

    String key = cache.catalogKey("page:" + safePage + ":size:" + safeSize + ":sort:" + safeSort + ":" + dir);
    var cached = cache.getJson(key, new TypeReference<PageResponse<ProductDto>>() {});
    if (cached.isPresent()) {
      return cached.get();
    }

    var pageable = PageRequest.of(safePage, safeSize, Sort.by(dir, safeSort));
    var result = PageResponse.of(productRepository.findAll(pageable).map(p -> productMapper.toDto(p)));
    cache.putJson(key, result);
    return result;
  }

  @Override
  public int refreshCache() {
    return cache.refreshAll();
  }

}