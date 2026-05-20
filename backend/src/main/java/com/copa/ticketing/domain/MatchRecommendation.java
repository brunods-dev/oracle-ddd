package com.copa.ticketing.domain;

public record MatchRecommendation(
        long matchId,
        String homeTeam,
        String awayTeam,
        String homeTeamCode,
        String awayTeamCode,
        String venueName,
        String matchAt,
        String reason
) {}
