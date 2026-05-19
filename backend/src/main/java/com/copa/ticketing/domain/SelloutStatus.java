package com.copa.ticketing.domain;

import java.util.List;

public record SelloutStatus(
        long matchId,
        String matchNumber,
        String matchStatus,
        long capacity,
        String homeTeam,
        String awayTeam,
        String venueName,
        String city,
        String country,
        String venueTimeZone,
        List<SectorStatus> sectors,
        Totals totals,
        StatusMix statusMix,
        long paidOrders,
        double revenue,
        long deltaSold,
        long deltaOccupied,
        boolean running,
        Integer activeMatchNumber,
        double progressPercent,
        List<SelloutEvent> events
) {

    public record SectorStatus(
            String sectorCode,
            long totalQuantity,
            long reservedQuantity,
            long soldQuantity,
            long availableQuantity,
            String status
    ) {}

    public record Totals(long total, long reserved, long sold, long available) {}

    public record StatusMix(
            long reservedOnlySeats,
            long paymentPendingSeats,
            long issuedSeats,
            long reservedOnlyReservations,
            long paymentPendingOrders,
            long paidOrders
    ) {}
}
