package com.example.userservice.config.client;


import com.example.userservice.config.context.ContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class RestTemplateConfig {

  private final ContextHolder contextHolder;
  private final RestTemplateBuilder restTemplateBuilder;

  @Bean
  public RestTemplateInterceptor restTemplateInterceptor() {
    return new RestTemplateInterceptor(contextHolder);
  }

  @Bean
  @LoadBalanced
  public RestTemplate getRestTemplate(RestTemplateInterceptor restTemplateInterceptor) {
    return restTemplateBuilder.interceptors(restTemplateInterceptor).build();
  }
}
