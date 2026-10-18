package com.example.notificationservice.queue;

import com.example.notificationservice.dto.OrderCreatedEvent;

public record NotificationJob(OrderCreatedEvent order) {
}
