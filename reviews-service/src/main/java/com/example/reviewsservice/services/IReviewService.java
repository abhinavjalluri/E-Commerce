package com.example.reviewsservice.services;

import com.example.reviewsservice.dtos.ProductAvgRatDto;
import com.example.reviewsservice.dtos.ReviewCreateDto;
import com.example.reviewsservice.dtos.ReviewDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface IReviewService {

  ReviewDto addOne(ReviewCreateDto reviewCreateDto);

  List<ReviewDto> getAllByProduct(Long productId);

  List<ReviewDto> getAllByUser();

  ReviewDto getOne(Long reviewId);

  ReviewDto updateOne(Long reviewId, ReviewCreateDto reviewCreateDto);

  List<ReviewDto> getAll();

  boolean deleteOne(Long reviewId);

  List<ProductAvgRatDto> getTopAvgRatedProducts(int max);

}
