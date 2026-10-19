package com.example.inventoryservice.services;

import com.example.inventoryservice.models.Product;
import com.example.inventoryservice.repository.IProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class ProductCacheService {

  private static final String KEY_PREFIX = "products:";
  private static final String INDEX_KEY = "products-index";
  private static final String VERSION_KEY = "catalog:version";

  private final StringRedisTemplate redisTemplate;
  private final IProductRepository productRepository;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final Duration pageTtl;
  private final int batchSize;

  public ProductCacheService(
    StringRedisTemplate redisTemplate,
    IProductRepository productRepository,
    ObjectMapper objectMapper,
    @Value("${product.cache.ttl:30m}") Duration ttl,
    @Value("${product.cache.page-ttl:2m}") Duration pageTtl,
    @Value("${product.cache.batch-size:500}") int batchSize
  ) {
    this.redisTemplate = redisTemplate;
    this.productRepository = productRepository;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
    this.pageTtl = pageTtl;
    this.batchSize = batchSize;
  }

  // ---------------- single product (hash) ----------------

  public Optional<Product> getProduct(Long id) {
    try {
      Map<Object, Object> h = redisTemplate.opsForHash().entries(KEY_PREFIX + id);
      return Optional.ofNullable(fromHash(id, h));
    } catch (Exception e) {
      log.warn("Redis read failed for product {}; falling back to DB", id, e);
      return Optional.empty();
    }
  }

  /** Cache hits only; ids missing from Redis are absent from the result. */
  public Map<Long, Product> getProducts(Collection<Long> ids) {
    Map<Long, Product> found = new LinkedHashMap<>();
    for (Long id : ids) {
      getProduct(id).ifPresent(p -> found.put(id, p));
    }
    return found;
  }

  public void cacheProduct(Product p) {
    String key = KEY_PREFIX + p.getId();
    redisTemplate.opsForHash().putAll(key, toHash(p));
    redisTemplate.expire(key, ttl);
    redisTemplate.opsForSet().add(INDEX_KEY, p.getId().toString());
  }

  /** Read-path fill: only writes if there is no complete entry, so it never overwrites a newer write. */
  public void cacheProductIfAbsent(Product p) {
    try {
      if (getProduct(p.getId()).isEmpty()) {
        cacheProduct(p);
      }
    } catch (Exception e) {
      log.warn("Redis fill failed for product {}", p.getId(), e);
    }
  }

  public void evictProduct(Long id) {
    redisTemplate.delete(KEY_PREFIX + id);
    redisTemplate.opsForSet().remove(INDEX_KEY, id.toString());
  }

  // ---------------- write hooks (run after the DB commit) ----------------

  public void cacheProductsAfterCommit(Collection<Product> products, boolean invalidateCatalog) {
    runAfterCommit(() -> products.forEach(this::cacheProduct), "cache products");
    if (invalidateCatalog) {
      runAfterCommit(this::bumpCatalogVersion, "bump catalog version");
    }
  }

  public void evictProductAfterCommit(Long id) {
    runAfterCommit(() -> evictProduct(id), "evict product " + id);
    runAfterCommit(this::bumpCatalogVersion, "bump catalog version");
  }

  // ---------------- catalog responses (JSON) ----------------

  /** Key includes the current version, so bumping the version invalidates every catalog entry at once. */
  public String catalogKey(String suffix) {
    return "catalog:v" + catalogVersion() + ":" + suffix;
  }

  public <T> Optional<T> getJson(String key, TypeReference<T> type) {
    try {
      String json = redisTemplate.opsForValue().get(key);
      return json == null ? Optional.empty() : Optional.ofNullable(objectMapper.readValue(json, type));
    } catch (Exception e) {
      log.warn("Redis/JSON read failed for {}", key, e);
      return Optional.empty();
    }
  }

  public void putJson(String key, Object value) {
    try {
      redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), pageTtl);
    } catch (Exception e) {
      log.warn("Redis/JSON write failed for {}", key, e);
    }
  }

  private String catalogVersion() {
    try {
      String v = redisTemplate.opsForValue().get(VERSION_KEY);
      return v == null ? "0" : v;
    } catch (Exception e) {
      return "0";
    }
  }

  private void bumpCatalogVersion() {
    redisTemplate.opsForValue().increment(VERSION_KEY);
  }

  // ---------------- full refresh ----------------

  public int refreshAll() {
    Set<String> previous = redisTemplate.opsForSet().members(INDEX_KEY);
    Set<String> current = new HashSet<>();

    int page = 0;
    Page<Product> result;
    do {
      result = productRepository.findAll(PageRequest.of(page++, batchSize, Sort.by("id")));
      List<Product> items = result.getContent();
      if (!items.isEmpty()) {
        writeBatch(items);
        String[] ids = items.stream().map(p -> p.getId().toString()).toArray(String[]::new);
        redisTemplate.opsForSet().add(INDEX_KEY, ids);
        current.addAll(Arrays.asList(ids));
      }
    } while (result.hasNext());

    if (previous != null && !previous.isEmpty()) {
      Set<String> stale = new HashSet<>(previous);
      stale.removeAll(current);
      if (!stale.isEmpty()) {
        redisTemplate.delete(stale.stream().map(id -> KEY_PREFIX + id).toList());
        redisTemplate.opsForSet().remove(INDEX_KEY, stale.toArray());
        log.info("Removed {} stale product cache entries", stale.size());
      }
    }
    bumpCatalogVersion(); // also repairs any drifted catalog responses
    return current.size();
  }

  private void writeBatch(List<Product> items) {
    RedisSerializer<String> s = redisTemplate.getStringSerializer();
    long ttlSeconds = ttl.toSeconds();

    redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
      for (Product p : items) {
        byte[] key = s.serialize(KEY_PREFIX + p.getId());
        Map<byte[], byte[]> hash = new HashMap<>();
        toHash(p).forEach((k, v) -> hash.put(s.serialize(k), s.serialize(v)));
        conn.hashCommands().hMSet(key, hash);
        conn.keyCommands().expire(key, ttlSeconds);
      }
      return null;
    });
  }

  // ---------------- helpers ----------------

  private Map<String, String> toHash(Product p) {
    Map<String, String> h = new HashMap<>();
    h.put("productId", p.getId().toString());
    h.put("name", p.getName());
    h.put("price", p.getPrice() == null ? "" : p.getPrice().toPlainString());
    h.put("quantity", String.valueOf(p.getQuantity()));
    h.put("description", p.getDescription() == null ? "" : p.getDescription());
    return h;
  }

  /** Returns null for missing or incomplete entries (e.g. written by an older version), which counts as a miss. */
  private Product fromHash(Long id, Map<Object, Object> h) {
    if (h == null || h.isEmpty()) return null;
    Object name = h.get("name");
    Object quantity = h.get("quantity");
    if (name == null || quantity == null) return null;

    Object price = h.get("price");
    Object description = h.get("description");
    return Product.builder()
      .id(id)
      .name(name.toString())
      .price(price == null || price.toString().isBlank() ? null : new BigDecimal(price.toString()))
      .quantity(Integer.parseInt(quantity.toString()))
      .description(description == null || description.toString().isEmpty() ? null : description.toString())
      .build();
  }

  private void runAfterCommit(Runnable action, String description) {
    Runnable safe = () -> {
      try {
        action.run();
      } catch (Exception e) {
        log.warn("Redis operation failed ({}); next refresh will fix it", description, e);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          safe.run();
        }
      });
    } else {
      safe.run();
    }
  }
}