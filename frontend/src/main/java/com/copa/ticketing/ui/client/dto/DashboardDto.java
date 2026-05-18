package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardDto(
        long totalOrders,
        long paidOrders,
        double grossRevenue,
        long ticketsSold,
        long activeReservations,
        double conversionPercent,
        List<TopMatchDto> topMatches,
        List<DailySaleDto> dailySales
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopMatchDto(
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailySaleDto(
            String saleDate,
            long ordersCreated,
            long paidOrders,
            double grossRevenue
    ) {}
}
