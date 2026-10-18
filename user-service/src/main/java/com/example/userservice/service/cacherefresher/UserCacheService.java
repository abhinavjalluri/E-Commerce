package com.example.userservice.service.cacherefresher;

import com.example.userservice.dto.UserCacheDto;
import com.example.userservice.models.User;
import com.example.userservice.repository.IUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class UserCacheService {

  private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);

  private static final String USER_KEY_PREFIX = "users:";
  private static final String USER_INDEX_KEY = "users-index"; // set of cached user ids

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

  /** Called manually after insert/update. */
  public void cacheUser(User user) {
    String key = USER_KEY_PREFIX + user.getId();
    redisTemplate.opsForHash().putAll(key, toHash(user.getId(), user.getName(), user.getEmail()));
    redisTemplate.expire(key, ttl);
    redisTemplate.opsForSet().add(USER_INDEX_KEY, user.getId().toString());
  }

  /** Called manually after delete. */
  public void evictUser(Long userId) {
    redisTemplate.delete(USER_KEY_PREFIX + userId);
    redisTemplate.opsForSet().remove(USER_INDEX_KEY, userId.toString());
  }

  /** Reads ALL users from DB in pages, writes them to Redis, removes entries for users no longer in DB. */
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

  private Map<String, String> toHash(Long id, String name, String email) {
    return Map.of("userId", id.toString(), "name", name, "email", email);
  }
}