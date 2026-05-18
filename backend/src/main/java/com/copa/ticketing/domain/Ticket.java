package com.copa.ticketing.domain;

import java.time.LocalDateTime;

public record Ticket(
        long id,
        String ticketCode,
        long orderId,
        long customerId,
        long matchId,
        String matchNumber,
        String homeTeam,
        String awayTeam,
        String venueName,
        String venueCity,
        LocalDateTime matchAt,
        String sectorName,
        String seatLabel,
        String gate,
        double unitPrice,
        String status,
        LocalDateTime issuedAt
) {}
