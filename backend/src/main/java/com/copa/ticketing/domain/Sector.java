package com.copa.ticketing.domain;

public record Sector(
        long matchSectorId,
        String sectorCode,
        String sectorName,
        long matchId,
        double price,
        long totalQuantity,
        long soldQuantity,
        long reservedQuantity,
        long availableQuantity,
        double occupancyPercent,
        String status
) {
    public static String computeStatus(long available) {
        return available > 0 ? "AVAILABLE" : "SOLD_OUT";
    }
}
