package com.example.notificationservice.service;

import com.example.notificationservice.dto.OrderCreatedEvent;
import com.example.notificationservice.dto.UserDetails;
import com.example.notificationservice.queue.NotificationJob;
import com.example.notificationservice.queue.NotificationQueue;
import com.example.notificationservice.rediscache.RedisUserService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class EmailNotificationService {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

  private final NotificationQueue notificationQueue;
  private final RedisUserService redisUserService;
  private final JavaMailSender mailSender;
  private final Executor emailExecutor;
  private final int workers;

  public EmailNotificationService(
    NotificationQueue notificationQueue,
    RedisUserService redisUserService,
    JavaMailSender mailSender,
    @Qualifier("emailExecutor") Executor emailExecutor,
    @Value("${notification.workers:4}") int workers
  ) {
    this.notificationQueue = notificationQueue;
    this.redisUserService = redisUserService;
    this.mailSender = mailSender;
    this.emailExecutor = emailExecutor;
    this.workers = workers;
  }

  @PostConstruct
  public void startWorkers() {
    for (int i = 0; i < workers; i++) {
      emailExecutor.execute(this::processQueue);
    }
  }

  private void processQueue() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        NotificationJob job = notificationQueue.take();
        processJob(job);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("Email worker interrupted");
      } catch (Exception e) {
        log.error("Unexpected notification worker failure", e);
      }
    }
  }


  private void processJob(NotificationJob job) {
    OrderCreatedEvent order = job.order();

    try {
      UserDetails user =
        redisUserService.getUser(order.userId())
          .orElseThrow(() ->
            new IllegalStateException(
              "User cache entry not found for userId: "
                + order.userId()
            )
          );

      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(user.email());
      message.setSubject("Order confirmation #" + order.id());
      message.setText(
        "Hello " + user.name() + ",\n\n"
          + "We received your order #" + order.id() + ".\n"
          + "Your order is being processed.\n\n"
          + "Thank you for shopping with us!"
      );

      mailSender.send(message);

      log.info(
        "Order confirmation sent: orderId={}, userId={}",
        order.id(),
        order.userId()
      );
    } catch (Exception e) {
      log.error(
        "Failed to process notification for orderId={}, userId={}",
        order.id(),
        order.userId(),
        e
      );

      // Production: retry with backoff and move exhausted
      // jobs to a durable dead-letter queue.
    }
  }

}