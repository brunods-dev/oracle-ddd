package com.copa.ticketing.domain;

import java.time.LocalDateTime;

public record Order(
        long id,
        String orderCode,
        long customerId,
        String customerName,
        String customerEmail,
        String paymentMethod,
        String status,
        double amount,
        String paymentReference,
        LocalDateTime createdAt
) {}
