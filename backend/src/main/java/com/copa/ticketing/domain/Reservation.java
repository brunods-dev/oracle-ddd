package com.copa.ticketing.domain;

import java.time.LocalDateTime;

public record Reservation(
        long id,
        String reservationCode,
        long matchId,
        long customerId,
        String status,
        double totalAmount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {}
