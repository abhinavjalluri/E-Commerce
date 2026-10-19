package com.example.notificationservice.rediscache;

import com.example.notificationservice.dto.ProductDetails;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RedisProductService {

  private static final String KEY_PREFIX = "products:";
  private static final List<Object> FIELDS = List.of("name", "description");

  private final StringRedisTemplate redisTemplate;

  public RedisProductService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /** Returns only the products found in the cache; missing ids are simply absent from the map. */
  public Map<Long, ProductDetails> getProducts(Collection<Long> productIds) {
    Map<Long, ProductDetails> result = new HashMap<>();
    for (Long id : new LinkedHashSet<>(productIds)) {
      if (id == null) continue;
      List<Object> values = redisTemplate.opsForHash().multiGet(KEY_PREFIX + id, FIELDS);
      Object name = values.get(0);
      if (name == null) continue; // not cached
      Object description = values.get(1);
      result.put(id, new ProductDetails(id, name.toString(), description == null ? "" : description.toString()));
    }
    return result;
  }
}