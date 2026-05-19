package com.copa.ticketing.service;

public record SelloutConfig(
        int matchNumber,
        int orderSize,
        int batchOrders,
        int maxSeats,
        double reservedPercent,
        double paymentPendingPercent,
        double issuedPercent,
        double paymentMethodCardRate
) {
    public SelloutConfig {
        if (orderSize < 1 || orderSize > 12)
            throw new IllegalArgumentException("orderSize must be between 1 and 12");
        if (batchOrders < 1 || batchOrders > 2000)
            throw new IllegalArgumentException("batchOrders must be between 1 and 2000");
        double total = reservedPercent + paymentPendingPercent + issuedPercent;
        if (Math.round(total * 100.0) / 100.0 != 100.0)
            throw new IllegalArgumentException("Status percentages must sum to 100");
    }

    public static SelloutConfig withDefaults(int matchNumber) {
        return new SelloutConfig(matchNumber, 4, 500, 0, 0, 0, 100, 0.74);
    }
}
