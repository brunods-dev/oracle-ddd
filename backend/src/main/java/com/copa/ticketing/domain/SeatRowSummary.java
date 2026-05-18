package com.copa.ticketing.domain;

public record SeatRowSummary(
        String rowLabel,
        long totalSeats,
        long availableCount,
        long reservedCount,
        long soldCount
) {}
