package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(
        long id,
        String matchNumber,
        String homeTeam,
        String awayTeam,
        String homeTeamCode,
        String awayTeamCode,
        String venueName,
        String venueCity,
        String venueCountry,
        String matchAt,
        String status,
        String competitionStage,
        String groupName,
        long totalCapacity,
        long ticketsSold,
        long availableSeats,
        double occupancyPercent
) {}
