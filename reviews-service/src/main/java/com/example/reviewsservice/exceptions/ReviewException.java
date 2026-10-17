package com.example.reviewsservice.exceptions;

import org.springframework.http.HttpStatusCode;

public class ReviewException extends RuntimeException {

  public static final String REVIEW_ALREADY_EXISTS = "There is already a review for that product";
  public static final String ORDER_DOES_NOT_EXIST = "Order does not exist";
  public static final String PRODUCT_NOT_PRESENT_IN_ORDER = "Product not present in the order";
  public HttpStatusCode statusCode;

  public ReviewException(String s) {
    super(s);
  }

  public ReviewException(HttpStatusCode statusCode) {
    this.statusCode = statusCode;
  }

}
