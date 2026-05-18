package com.copa.ticketing.domain;

public record Seat(
        long venueSeatId,
        long matchId,
        long matchSectorId,
        String sectorCode,
        String sectorName,
        String rowLabel,
        int seatNumber,
        String seatLabel,
        String blockCode,
        String entrance,
        double price,
        String status,
        boolean isOptimal
) {}
