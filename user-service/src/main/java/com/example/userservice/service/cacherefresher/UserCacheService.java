package com.example.userservice.service.cacherefresher;

import com.example.userservice.dto.UserCacheDto;
import com.example.userservice.models.User;
import com.example.userservice.repository.IUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class UserCacheService {

  private static final String USER_KEY_PREFIX = "users:";
  private static final String USER_INDEX_KEY = "users-index";

  private final StringRedisTemplate redisTemplate;
  private final IUserRepository userRepository;
  private final Duration ttl;
  private final int batchSize;

  public UserCacheService(
    StringRedisTemplate redisTemplate,
    IUserRepository userRepository,
    @Value("${user.cache.ttl:30m}") Duration ttl,
    @Value("${user.cache.batch-size:500}") int batchSize
  ) {
    this.redisTemplate = redisTemplate;
    this.userRepository = userRepository;
    this.ttl = ttl;
    this.batchSize = batchSize;
  }

  public void cacheUser(User user) {
    String key = USER_KEY_PREFIX + user.getId();
    redisTemplate.opsForHash().putAll(key, Map.of(
      "userId", user.getId().toString(),
      "name", user.getName(),
      "email", user.getEmail()
    ));
    redisTemplate.expire(key, ttl);
    redisTemplate.opsForSet().add(USER_INDEX_KEY, user.getId().toString());
  }

  public void evictUser(Long userId) {
    redisTemplate.delete(USER_KEY_PREFIX + userId);
    redisTemplate.opsForSet().remove(USER_INDEX_KEY, userId.toString());
  }

  public void cacheUserAfterCommit(User user) {
    runAfterCommit(() -> cacheUser(user), "cache user " + user.getId());
  }

  public void evictUserAfterCommit(Long userId) {
    runAfterCommit(() -> evictUser(userId), "evict user " + userId);
  }

  /** Reads all users from DB page by page, writes to Redis, removes stale entries. */
  public int refreshAll() {
    Set<String> previous = redisTemplate.opsForSet().members(USER_INDEX_KEY);
    Set<String> current = new HashSet<>();

    int page = 0;
    Page<UserCacheDto> result;
    do {
      result = userRepository.findAllForCache(PageRequest.of(page++, batchSize));
      List<UserCacheDto> users = result.getContent();
      if (!users.isEmpty()) {
        writeBatch(users);
        String[] ids = users.stream().map(u -> u.userId().toString()).toArray(String[]::new);
        redisTemplate.opsForSet().add(USER_INDEX_KEY, ids);
        current.addAll(Arrays.asList(ids));
      }
    } while (result.hasNext());

    if (previous != null && !previous.isEmpty()) {
      Set<String> stale = new HashSet<>(previous);
      stale.removeAll(current);
      if (!stale.isEmpty()) {
        redisTemplate.delete(stale.stream().map(id -> USER_KEY_PREFIX + id).toList());
        redisTemplate.opsForSet().remove(USER_INDEX_KEY, stale.toArray());
        log.info("Removed {} stale user cache entries", stale.size());
      }
    }
    return current.size();
  }

  private void writeBatch(List<UserCacheDto> users) {
    RedisSerializer<String> s = redisTemplate.getStringSerializer();
    long ttlSeconds = ttl.toSeconds();

    redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
      for (UserCacheDto u : users) {
        byte[] key = s.serialize(USER_KEY_PREFIX + u.userId());
        Map<byte[], byte[]> hash = new HashMap<>();
        hash.put(s.serialize("userId"), s.serialize(u.userId().toString()));
        hash.put(s.serialize("name"), s.serialize(u.name()));
        hash.put(s.serialize("email"), s.serialize(u.email()));
        conn.hashCommands().hMSet(key, hash);
        conn.keyCommands().expire(key, ttlSeconds);
      }
      return null;
    });
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