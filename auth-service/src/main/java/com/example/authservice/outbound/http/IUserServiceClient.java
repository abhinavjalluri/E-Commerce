package com.example.authservice.outbound.http;

import com.example.authservice.dtos.LoginDto;
import com.example.authservice.dtos.UserDto;

import java.util.Optional;

public interface IUserServiceClient {
  Optional<UserDto> getUserForLogin(LoginDto loginDto);
}
