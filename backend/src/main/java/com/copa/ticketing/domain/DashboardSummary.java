package com.copa.ticketing.domain;

import java.util.List;

public record DashboardSummary(
        long totalOrders,
        long paidOrders,
        double grossRevenue,
        long ticketsSold,
        long activeReservations,
        double conversionPercent,
        List<TopMatch> topMatches,
        List<DailySale> dailySales
) {
    public record TopMatch(
            long matchId,
            String matchNumber,
            String homeTeam,
            String awayTeam,
            String venueName,
            String venueCity,
            long ticketsSold,
            long totalCapacity,
            double grossRevenue,
            double occupancyPercent
    ) {}

    public record DailySale(
            String saleDate,
            long ordersCreated,
            long paidOrders,
            double grossRevenue
    ) {}
}
