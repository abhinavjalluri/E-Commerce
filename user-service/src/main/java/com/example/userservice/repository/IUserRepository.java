package com.example.userservice.repository;

import com.example.userservice.dto.UserCacheDto;
import com.example.userservice.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IUserRepository extends JpaRepository<User, Long> {

  @Query("SELECT COUNT(u) > 0 FROM users u WHERE u.username = :#{#user.username} OR u.email = :#{#user.email}")
  boolean existsUser(@Param("user") User user);

  Optional<User> findByUsername(String username);

  Optional<User> findByUsernameAndPassword(String username, String password);

  @Query(
    value = "SELECT new com.example.userservice.dto.UserCacheDto(u.id, u.name, u.email) FROM users u ORDER BY u.id",
    countQuery = "SELECT COUNT(u) FROM users u"
  )
  Page<UserCacheDto> findAllForCache(Pageable pageable);
}