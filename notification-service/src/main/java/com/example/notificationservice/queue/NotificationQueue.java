package com.example.notificationservice.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
public class NotificationQueue {

  private final BlockingQueue<NotificationJob> queue;

  public NotificationQueue(
    @Value("${notification.queue.capacity:10000}") int capacity
  ) {
    if (capacity <= 0) {
      throw new IllegalArgumentException(
        "Notification queue capacity must be positive"
      );
    }

    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  public boolean enqueue(NotificationJob job) {
    return queue.offer(job);
  }

  public NotificationJob take() throws InterruptedException {
    return queue.take();
  }

  public int size() {
    return queue.size();
  }
}