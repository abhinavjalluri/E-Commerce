package com.example.notificationservice.kafka;

import com.example.notificationservice.dto.OrderCreatedEvent;
import com.example.notificationservice.queue.NotificationJob;
import com.example.notificationservice.queue.NotificationQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

  private final ObjectMapper objectMapper;
  private final NotificationQueue notificationQueue;

  public OrderConsumer(ObjectMapper objectMapper, NotificationQueue notificationQueue) {
    this.objectMapper = objectMapper;
    this.notificationQueue = notificationQueue;
  }

  @KafkaListener(
    id = "order-consumer",
    topics = "${api.kafka.topics.order-created}",
    groupId = "order-group"
  )
  public void listen(String value, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic, @Header(KafkaHeaders.RECEIVED_KEY) String key) throws Exception {

    OrderCreatedEvent event = objectMapper.readValue(value, OrderCreatedEvent.class);
    if (event.id() == null || event.userId() == null) {
      throw new IllegalArgumentException(
        "Order event must contain order ID and user ID"
      );
    }

    NotificationJob job = new NotificationJob(event);

    if (!notificationQueue.enqueue(job)) {
      log.error(
        "Notification queue full; orderId={}, userId={}, topic={}",
        event.id(),
        event.userId(),
        topic
      );

      throw new IllegalStateException(
        "Notification queue is full"
      );
    }

    log.info(
      "Order event queued successfully: topic={}, key={}, orderId={}, userId={}, queueSize={}",
      topic,
      key,
      event.id(),
      event.userId(),
      notificationQueue.size()
    );
  }

}