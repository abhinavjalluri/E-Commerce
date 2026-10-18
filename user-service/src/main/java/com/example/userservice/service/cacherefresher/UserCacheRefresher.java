package com.example.userservice.service.cacherefresher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserCacheRefresher {

  private static final Logger log = LoggerFactory.getLogger(UserCacheRefresher.class);

  private final UserCacheService userCacheService;

  public UserCacheRefresher(UserCacheService userCacheService) {
    this.userCacheService = userCacheService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void warmUp() {
    refresh("startup");
  }

  @Scheduled(
    fixedDelayString = "${user.cache.refresh-interval-ms:300000}",
    initialDelayString = "${user.cache.refresh-interval-ms:300000}"
  )
  public void scheduledRefresh() {
    refresh("scheduled");
  }

  private void refresh(String trigger) {
    try {
      long start = System.currentTimeMillis();
      int count = userCacheService.refreshAll();
      log.info("User cache refresh ({}) cached {} users in {} ms",
        trigger, count, System.currentTimeMillis() - start);
    } catch (Exception e) {
      // never let the scheduler die; next run will retry
      log.error("User cache refresh ({}) failed", trigger, e);
    }
  }
}