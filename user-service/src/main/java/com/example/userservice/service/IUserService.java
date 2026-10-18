package com.example.userservice.service;

import com.example.userservice.dto.*;

public interface IUserService {
  UserDto addOne(UserCreateDto userCreateDto);
  UserDto getUser();
  UserDto getUserLogin(LoginDto loginDto);
  UserDto getUserById(Long id);
  PageResponse<UserDto> getAllUsers(int page, int size, String sortBy, String direction);
  UserDto editUser(UserEditDto userEditDto);
  void deleteUser(Long id);
  int refreshCache();
}