package com.example.userservice.service;

import com.example.userservice.config.context.ContextHolder;
import com.example.userservice.dto.*;
import com.example.userservice.exceptions.UserException;
import com.example.userservice.mapper.IUserMapper;
import com.example.userservice.repository.IUserRepository;
import com.example.userservice.service.cacherefresher.UserCacheService;
import com.example.userservice.utils.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements IUserService {

  private static final Set<String> SORTABLE = Set.of("id", "name", "username", "email", "createdAt", "updatedAt");
  private static final int MAX_PAGE_SIZE = 100;

  private final IUserRepository userRepository;
  private final IUserMapper userMapper;
  private final ContextHolder contextHolder;
  private final UserCacheService userCacheService;

  @Override
  @Transactional
  public UserDto addOne(UserCreateDto userCreateDto) {
    var password = HashUtils.sha256Hash(userCreateDto.password());
    var user = userMapper.toUser(userCreateDto, password);
    if (userRepository.existsUser(user)) {
      throw new UserException(UserException.USER_ALREADY_EXISTS);
    }
    var saved = userRepository.save(user);
    userCacheService.cacheUserAfterCommit(saved);
    return userMapper.toDto(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public UserDto getUser() {
    var user = userRepository.findByUsername(contextHolder.getUsername())
      .orElseThrow(() -> new UserException(UserException.USER_NOT_FOUND));
    return userMapper.toDto(user);
  }

  @Override
  @Transactional(readOnly = true)
  public UserDto getUserLogin(LoginDto loginDto) {
    var password = HashUtils.sha256Hash(loginDto.password());
    var user = userRepository.findByUsernameAndPassword(loginDto.username(), password)
      .orElseThrow(() -> new UserException(UserException.USER_NOT_FOUND));
    return userMapper.toDto(user);
  }

  @Override
  @Transactional(readOnly = true)
  public UserDto getUserById(Long id) {
    var user = userRepository.findById(id)
      .orElseThrow(() -> new UserException(UserException.USER_NOT_FOUND));
    return userMapper.toDto(user);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<UserDto> getAllUsers(int page, int size, String sortBy, String direction) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    String safeSort = SORTABLE.contains(sortBy) ? sortBy : "id";
    Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

    var pageable = PageRequest.of(safePage, safeSize, Sort.by(dir, safeSort));
    return PageResponse.of(userRepository.findAll(pageable).map(userMapper::toDto));
  }

  @Override
  @Transactional
  public UserDto editUser(UserEditDto userEditDto) {
    var user = userRepository.findById(userEditDto.id())
      .orElseThrow(() -> new UserException(UserException.USER_NOT_FOUND));
    userMapper.partialUpdate(userEditDto, user);
    var edited = userRepository.save(user);
    userCacheService.cacheUserAfterCommit(edited);
    return userMapper.toDto(edited);
  }

  @Override
  @Transactional
  public void deleteUser(Long id) {
    var user = userRepository.findById(id)
      .orElseThrow(() -> new UserException(UserException.USER_NOT_FOUND));
    userRepository.delete(user);
    userCacheService.evictUserAfterCommit(id);
  }

  @Override
  public int refreshCache() {
    return userCacheService.refreshAll();
  }
}