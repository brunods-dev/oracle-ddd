package com.copa.ticketing.ui.client.dto;

import java.util.Map;

public record SelloutStartRequest(
        int matchNumber,
        int orderSize,
        int batchOrders,
        int maxSeats,
        Map<String, Object> statusMix
) {
    public static SelloutStartRequest of(int matchNumber, double reservedPct, double pendingPct, double issuedPct) {
        return new SelloutStartRequest(matchNumber, 4, 1000, 0,
                Map.of("reservedPercent", reservedPct,
                        "paymentPendingPercent", pendingPct,
                        "issuedPercent", issuedPct));
    }
}
