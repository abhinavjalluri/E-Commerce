package com.example.inventoryservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheRefresher {

  private final ProductCacheService cacheService;

  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    refresh("startup");
  }

  @Scheduled(
    fixedDelayString = "${product.cache.refresh-interval-ms:300000}",
    initialDelayString = "${product.cache.refresh-interval-ms:300000}"
  )
  public void scheduledRefresh() {
    refresh("scheduled");
  }

  private void refresh(String trigger) {
    try {
      long start = System.currentTimeMillis();
      int count = cacheService.refreshAll();
      log.info("Product cache refresh ({}) cached {} products in {} ms", trigger, count, System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.error("Product cache refresh ({}) failed", trigger, e);
    }
  }
}