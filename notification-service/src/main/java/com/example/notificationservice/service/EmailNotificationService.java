package com.example.notificationservice.service;

import com.example.notificationservice.dto.OrderCreatedEvent;
import com.example.notificationservice.dto.ProductDetails;
import com.example.notificationservice.dto.UserDetails;
import com.example.notificationservice.queue.NotificationJob;
import com.example.notificationservice.queue.NotificationQueue;
import com.example.notificationservice.rediscache.RedisProductService;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class EmailNotificationService {

  private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
  private static final String LINE = "--------------------------------------------------\n";
  private static final String DOUBLE_LINE = "==================================================\n";
  private static final int MAX_DESCRIPTION_LENGTH = 300;

  private final NotificationQueue notificationQueue;
  private final RedisUserService redisUserService;
  private final RedisProductService redisProductService;
  private final JavaMailSender mailSender;
  private final Executor emailExecutor;
  private final int workers;

  public EmailNotificationService(
    NotificationQueue notificationQueue,
    RedisUserService redisUserService,
    RedisProductService redisProductService,
    JavaMailSender mailSender,
    @Qualifier("emailExecutor") Executor emailExecutor,
    @Value("${notification.workers:4}") int workers
  ) {
    this.notificationQueue = notificationQueue;
    this.redisUserService = redisUserService;
    this.redisProductService = redisProductService;
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
        .orElseThrow(() -> new IllegalStateException(
          "User cache entry not found for userId: " + order.userId()));

      List<OrderCreatedEvent.OrderProduct> items =
        order.products() == null ? List.of() : order.products();

      // Product enrichment must never block the mail
      Map<Long, ProductDetails> details = Map.of();
      try {
        details = redisProductService.getProducts(
          items.stream().map(OrderCreatedEvent.OrderProduct::productId).toList());
      } catch (Exception e) {
        log.warn("Product cache lookup failed for orderId={}; sending mail without product details",
          order.id(), e);
      }

      BigDecimal totalAmount = BigDecimal.ZERO;
      int totalQuantity = 0;

      StringBuilder productDetails = new StringBuilder();
      productDetails.append("ORDER ITEMS\n").append(LINE);

      for (OrderCreatedEvent.OrderProduct item : items) {
        BigDecimal price = item.price() == null ? BigDecimal.ZERO : item.price();
        int quantity = item.quantity() == null ? 0 : item.quantity();
        BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(quantity));

        totalAmount = totalAmount.add(itemTotal);
        totalQuantity += quantity;

        ProductDetails product = details.get(item.productId());
        String name = product != null ? product.name() : "Product #" + item.productId();

        productDetails.append("Product: ").append(name).append("\n");
        if (product != null && product.description() != null && !product.description().isBlank()) {
          productDetails.append("Description: ").append(truncate(product.description())).append("\n");
        }
        productDetails.append("Quantity: ").append(quantity).append("\n");
        productDetails.append("Price per unit: ").append(price).append("\n");
        productDetails.append("Item total: ").append(itemTotal).append("\n");
        productDetails.append(LINE);
      }

      StringBuilder emailBody = new StringBuilder();
      emailBody.append("Hello ").append(user.name()).append(",\n\n");
      emailBody.append("Thank you for shopping with us!\n");
      emailBody.append("Your order has been received successfully.\n\n");

      emailBody.append("ORDER DETAILS\n").append(DOUBLE_LINE);
      emailBody.append("Order ID: ").append(order.id()).append("\n");
      if (order.createdAt() != null) {
        emailBody.append("Order Date: ").append(order.createdAt().format(DATE_FORMATTER)).append("\n");
      }

      emailBody.append("\n").append(productDetails);

      emailBody.append("\nORDER SUMMARY\n").append(DOUBLE_LINE);
      emailBody.append("Total Products: ").append(items.size()).append("\n");
      emailBody.append("Total Quantity: ").append(totalQuantity).append("\n");
      emailBody.append("Total Order Amount: ").append(totalAmount).append("\n\n");

      emailBody.append("Your order is being processed.\n");
      emailBody.append("We will keep you updated on its status.\n\n");
      emailBody.append("Thank you for shopping with us!\n");
      emailBody.append("Regards,\n");
      emailBody.append("E-Commerce Store Team");

      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(user.email());
      message.setSubject("Order Confirmation #" + order.id());
      message.setText(emailBody.toString());

      mailSender.send(message);

      log.info("Order confirmation sent: orderId={}, userId={}", order.id(), order.userId());

    } catch (Exception e) {
      log.error("Failed to process notification for orderId={}, userId={}",
        order.id(), order.userId(), e);
      // Production: retry with backoff and move exhausted jobs to a dead-letter queue.
    }
  }

  private String truncate(String text) {
    String cleaned = text.replaceAll("\\s+", " ").trim();
    return cleaned.length() <= MAX_DESCRIPTION_LENGTH
      ? cleaned
      : cleaned.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
  }
}