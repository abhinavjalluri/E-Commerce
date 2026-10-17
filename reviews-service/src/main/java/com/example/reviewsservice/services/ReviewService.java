package com.example.reviewsservice.services;

import com.example.reviewsservice.config.context.ContextHolder;
import com.example.reviewsservice.exceptions.ReviewException;
import com.example.reviewsservice.dtos.*;
import com.example.reviewsservice.mapper.IReviewMapper;
import com.example.reviewsservice.models.Review;
import com.example.reviewsservice.outbound.http.IOrderServiceClient;
import com.example.reviewsservice.repository.IReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

  private final IReviewMapper reviewMapper;
  private final IReviewRepository reviewRepository;
  private final IOrderServiceClient orderServiceClient;
  private final ContextHolder context;

  private static boolean isProductPresentInOrder(Long productId, OrderDto order) {
    return order.products()
      .stream()
      .anyMatch(orderProductDto -> orderProductDto.productId()
        .equals(productId));
  }

  @Override
  public ReviewDto addOne(ReviewCreateDto reviewCreateDto) {
    var existsReview = reviewRepository.existsByUserIdAndOrderIdAndProductId(
      context.getUserId(),
      reviewCreateDto.orderId(),
      reviewCreateDto.productId()
    );
    if (existsReview) {
      throw new ReviewException(ReviewException.REVIEW_ALREADY_EXISTS);
    }
    var order = orderServiceClient.getOrder(reviewCreateDto.orderId())
      .orElseThrow(() -> new ReviewException(ReviewException.ORDER_DOES_NOT_EXIST));
    if (!isProductPresentInOrder(reviewCreateDto.productId(), order)) {
      throw new ReviewException(ReviewException.PRODUCT_NOT_PRESENT_IN_ORDER);
    }
    var review = reviewMapper.toReview(reviewCreateDto, context.getUserId());
    var reviewSaved = reviewRepository.save(review);
    return reviewMapper.toDto(reviewSaved);
  }

  @Override
  public List<ReviewDto> getAllByUser() {
    var repositoryByUser = reviewRepository.findByUserId(context.getUserId());
    return reviewMapper.toDto(repositoryByUser);
  }

  @Override
  public List<ReviewDto> getAllByProduct(Long productId) {
    var reviewsByProduct = reviewRepository.findByProductId(productId);
    return reviewMapper.toDto(reviewsByProduct);
  }

  @Override
  public boolean deleteOne(Long reviewId) {
    return reviewRepository.deleteOneById(reviewId) == 1;
  }

  @Override
  public List<ProductAvgRatDto> getTopAvgRatedProducts(int max) {
    return reviewRepository.findAll()
      .stream()
      .collect(Collectors.groupingBy(
        Review::getProductId,
        Collectors.averagingDouble(Review::getRating)
      ))
      .entrySet()
      .stream()
      .sorted((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()))
      .limit(max)
      .map(longDoubleEntry ->
        new ProductAvgRatDto(
          longDoubleEntry.getKey(),
          BigDecimal.valueOf(longDoubleEntry.getValue())
        ))
      .toList();
  }

}
