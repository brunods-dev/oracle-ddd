package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketDto(
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
        String matchAt,
        String sectorName,
        String seatLabel,
        String gate,
        double unitPrice,
        String status,
        String issuedAt
) {}
