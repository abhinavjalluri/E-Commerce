package com.example.notificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfig {

  @Bean(name = "emailExecutor")
  public ThreadPoolTaskExecutor emailExecutor(@Value("${notification.workers:4}") int workers) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(workers);
    executor.setMaxPoolSize(workers);
    executor.setThreadNamePrefix("email-worker-");
    executor.setWaitForTasksToCompleteOnShutdown(false); // interrupts workers on shutdown
    return executor;
  }
}