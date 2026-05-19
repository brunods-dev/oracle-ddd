package com.copa.ticketing.domain;

import java.util.List;

public record DashboardStatus(
        boolean ok,
        boolean running,
        Summary summary,
        List<LabelValue> countries,
        List<LabelValue> sectors,
        List<LabelValue> payments,
        List<MatchBar> matches,
        List<HeatBlockItem> heatBlocks,
        List<Event> events
) {

    public record Summary(
            double revenue,
            long tickets,
            long paidOrders,
            long activeReservations,
            long createdReservations,
            long convertedReservations,
            double pendingAmount,
            double conversionPercent,
            double occupancyPercent
    ) {}

    public record LabelValue(String label, double value) {}

    public record MatchBar(String label, double value, long tickets) {}

    public record HeatBlockItem(String label, double heat) {}

    public record Event(String title, String detail) {}
}
