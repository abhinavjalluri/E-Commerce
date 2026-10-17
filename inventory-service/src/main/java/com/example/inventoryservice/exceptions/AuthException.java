package com.example.inventoryservice.exceptions;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

  public final HttpStatus httpStatus;

  public AuthException(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }
}
