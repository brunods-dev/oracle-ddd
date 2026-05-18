package com.copa.ticketing.domain;

import java.time.LocalDateTime;

public record Match(
        long id,
        String matchNumber,
        String homeTeam,
        String awayTeam,
        String homeTeamCode,
        String awayTeamCode,
        String venueName,
        String venueCity,
        String venueCountry,
        LocalDateTime matchAt,
        String status,
        String competitionStage,
        String groupName,
        long totalCapacity,
        long ticketsSold,
        long availableSeats,
        double occupancyPercent
) {}
