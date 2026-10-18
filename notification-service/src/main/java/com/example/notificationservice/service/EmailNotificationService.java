
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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;

@Service
public class EmailNotificationService {

  private static final Logger log =
    LoggerFactory.getLogger(EmailNotificationService.class);

  private static final DateTimeFormatter DATE_FORMATTER =
    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

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
      UserDetails user = redisUserService.getUser(order.userId())
        .orElseThrow(() ->
          new IllegalStateException(
            "User cache entry not found for userId: "
              + order.userId()
          )
        );

      BigDecimal totalAmount = BigDecimal.ZERO;

      StringBuilder productDetails = new StringBuilder();

      productDetails.append("ORDER ITEMS\n");
      productDetails.append("--------------------------------------------------\n");

      for (OrderCreatedEvent.OrderProduct product : order.products()) {
        BigDecimal itemTotal = product.price()
          .multiply(BigDecimal.valueOf(product.quantity()));

        totalAmount = totalAmount.add(itemTotal);

        productDetails.append("Product ID: ")
          .append(product.productId())
          .append("\n");

        productDetails.append("Quantity: ")
          .append(product.quantity())
          .append("\n");

        productDetails.append("Price per unit: ")
          .append(product.price())
          .append("\n");

        productDetails.append("Item total: ")
          .append(itemTotal)
          .append("\n");

        productDetails.append("--------------------------------------------------\n");
      }

      SimpleMailMessage message = new SimpleMailMessage();

      message.setTo(user.email());
      message.setSubject(
        "Order Confirmation #" + order.id()
      );

      StringBuilder emailBody = new StringBuilder();

      emailBody.append("Hello ")
        .append(user.name())
        .append(",\n\n");

      emailBody.append("Thank you for shopping with us!\n");
      emailBody.append("Your order has been received successfully.\n\n");

      emailBody.append("ORDER DETAILS\n");
      emailBody.append("==================================================\n");

      emailBody.append("Order ID: ")
        .append(order.id())
        .append("\n");

      emailBody.append("User ID: ")
        .append(order.userId())
        .append("\n");

      if (order.createdAt() != null) {
        emailBody.append("Order Date: ")
          .append(order.createdAt().format(DATE_FORMATTER))
          .append("\n");
      }

      emailBody.append("\n")
        .append(productDetails);

      emailBody.append("\nORDER SUMMARY\n");
      emailBody.append("==================================================\n");

      emailBody.append("Total Products: ")
        .append(order.products().size())
        .append("\n");

      int totalQuantity = order.products().stream()
        .mapToInt(product -> product.quantity())
        .sum();

      emailBody.append("Total Quantity: ")
        .append(totalQuantity)
        .append("\n");

      emailBody.append("Total Order Amount: ")
        .append(totalAmount)
        .append("\n\n");

      emailBody.append("Your order is being processed.\n");
      emailBody.append("We will keep you updated on its status.\n\n");

      emailBody.append("Thank you for shopping with us!\n");
      emailBody.append("Regards,\n");
      emailBody.append("E-Commerce Store Team");

      message.setText(emailBody.toString());

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
    }
  }
}
