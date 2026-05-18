package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(
        long id,
        String orderCode,
        long customerId,
        String customerName,
        String customerEmail,
        String paymentMethod,
        String status,
        double amount,
        String paymentReference,
        String createdAt
) {}
