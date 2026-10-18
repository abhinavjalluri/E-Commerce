
package com.example.notificationservice.rediscache;

import com.example.notificationservice.dto.UserDetails;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class RedisUserService {

  private static final String USER_KEY_PREFIX = "users:";

  private final StringRedisTemplate redisTemplate;

  public RedisUserService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public Optional<UserDetails> getUser(Long userId) {
    String key = USER_KEY_PREFIX + userId;

    Map<Object, Object> userData =
      redisTemplate.opsForHash().entries(key);

    if (userData.isEmpty()) {
      return Optional.empty();
    }

    Object name = userData.get("name");
    Object email = userData.get("email");

    if (name == null || email == null
      || email.toString().isBlank()) {
      return Optional.empty();
    }

    return Optional.of(new UserDetails(
      userId,
      name.toString(),
      email.toString()
    ));
  }

}
