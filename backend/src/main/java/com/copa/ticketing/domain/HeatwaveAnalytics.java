package com.copa.ticketing.domain;

import java.util.List;
import java.util.Map;

public record HeatwaveAnalytics(
        boolean ok,
        String engine,
        String forcedHint,
        Summary summary,
        List<TopMatch> topMatches,
        List<HostCountry> hostCountries,
        List<SectorDemand> sectorDemand,
        List<PaymentStatus> paymentStatus,
        List<HeatBlock> heatmap,
        LoadStatus loadStatus
) {

    public record Summary(
            double grossRevenue,
            long ticketsIssued,
            long paidOrders,
            long activeReservations,
            long reservationsCreated,
            long convertedReservations,
            double paymentPendingAmount,
            double conversionPercent,
            double occupancyPercent
    ) {
        public static Summary fromRow(Map<String, Object> row) {
            return new Summary(
                    num(row, "gross_revenue"),
                    lng(row, "tickets_issued"),
                    lng(row, "paid_orders"),
                    lng(row, "active_reservations"),
                    lng(row, "reservations_created"),
                    lng(row, "converted_reservations"),
                    num(row, "payment_pending_amount"),
                    num(row, "conversion_percent"),
                    num(row, "occupancy_percent")
            );
        }
    }

    public record TopMatch(
            String matchNumber,
            String homeTeam,
            String awayTeam,
            String venueName,
            String city,
            String country,
            double occupancyPercent,
            double grossRevenue,
            long ticketsIssued,
            long paymentPendingOrders,
            double paymentPendingAmount
    ) {}

    public record HostCountry(
            String country,
            long matchesCount,
            long totalMatchCapacity,
            long paidOrders,
            long ticketsIssued,
            double grossRevenue,
            double confirmedOccupancyPercent
    ) {}

    public record SectorDemand(
            String matchNumber,
            String homeTeam,
            String awayTeam,
            String venueName,
            String sectorCode,
            double occupancyPercent,
            double grossRevenue,
            long ticketsIssued,
            long availableQuantity
    ) {}

    public record PaymentStatus(
            String paymentMethod,
            String paymentStatus,
            long paymentCount,
            double totalAmount,
            double avgPaymentAmount
    ) {}

    public record HeatBlock(
            String matchNumber,
            String homeTeam,
            String awayTeam,
            String sectorCode,
            String blockCode,
            double heatPercent,
            String demandBand,
            long totalSeats,
            long reservedSeats,
            long soldSeats
    ) {}

    public record LoadStatus(
            long baseTables,
            long rapidTables,
            long loadedTables
    ) {}

    private static double num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static long lng(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }
}
