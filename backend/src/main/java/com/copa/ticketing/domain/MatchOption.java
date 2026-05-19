package com.copa.ticketing.domain;

public record MatchOption(
        long matchId,
        String matchNumber,
        String groupName,
        String matchAt,
        String matchStatus,
        long capacity,
        String homeTeam,
        String awayTeam,
        String venueName,
        String city,
        String country,
        String venueTimeZone,
        long totalQuantity,
        long reservedQuantity,
        long soldQuantity,
        long availableQuantity
) {}
