package com.example.notificationservice.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumer {

  private static final Logger log =
    LoggerFactory.getLogger(OrderConsumer.class);

  private static final String TEST_RECIPIENT =
    "jalluriabhinav@gmail.com";

  private final JavaMailSender mailSender;

  public OrderConsumer(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @KafkaListener(
    id = "order-consumer",
    topics = "${api.kafka.topics.order-created}",
    groupId = "order-group"
  )
  public void listen(
    String value,
    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
    @Header(KafkaHeaders.RECEIVED_KEY) String key
  ) {
    log.info(
      "CONSUMED EVENT FROM TOPIC: {} KEY = {} VALUE = {}",
      topic, key, value
    );

    sendMail(
      "Your order was received",
      "We received your order.\n\nOrder event: " + value
    );
  }


  private void sendMail(String subject, String body) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(TEST_RECIPIENT);
      message.setSubject(subject);
      message.setText(body);

      mailSender.send(message);

      log.info("Email sent successfully to {}", TEST_RECIPIENT);
    } catch (Exception e) {
      log.error("Failed to send email to {}", TEST_RECIPIENT, e);
    }
  }
}