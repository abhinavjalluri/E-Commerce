package com.example.inventoryservice.services;

import com.example.inventoryservice.dtos.*;
import java.util.List;

public interface IProductService {
  ProductDto addOne(ProductCreateDto productCreateDto);
  ProductDto updateOne(Long id, ProductCreateDto productCreateDto);
  void deleteOne(Long id);
  List<ProductDto> getAll();
  PageResponse<ProductDto> getPage(int page, int size, String sortBy, String direction);
  ProductDto getOneById(Long id);
  List<ProductDto> getAllByIds(List<Long> ids);
  void increaseStock(List<ProductStockQuantityDto> productsQuantities);
  void decreaseStock(List<ProductStockQuantityDto> productsQuantities);
  int refreshCache();
}