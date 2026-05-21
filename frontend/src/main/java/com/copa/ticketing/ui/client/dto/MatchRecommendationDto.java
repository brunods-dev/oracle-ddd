package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchRecommendationDto(
        long matchId,
        String homeTeam,
        String awayTeam,
        String homeTeamCode,
        String awayTeamCode,
        String venueName,
        String matchAt,
        String reason
) {}
