package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardStatusDto(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("running") boolean running,
        @JsonProperty("summary") Summary summary,
        @JsonProperty("countries") List<LabelValue> countries,
        @JsonProperty("sectors") List<LabelValue> sectors,
        @JsonProperty("payments") List<LabelValue> payments,
        @JsonProperty("matches") List<MatchBar> matches,
        @JsonProperty("heatBlocks") List<HeatBlockItem> heatBlocks,
        @JsonProperty("events") List<DashEvent> events
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            @JsonProperty("revenue") double revenue,
            @JsonProperty("tickets") long tickets,
            @JsonProperty("paidOrders") long paidOrders,
            @JsonProperty("activeReservations") long activeReservations,
            @JsonProperty("createdReservations") long createdReservations,
            @JsonProperty("convertedReservations") long convertedReservations,
            @JsonProperty("pendingAmount") double pendingAmount,
            @JsonProperty("conversionPercent") double conversionPercent,
            @JsonProperty("occupancyPercent") double occupancyPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelValue(@JsonProperty("label") String label, @JsonProperty("value") double value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchBar(
            @JsonProperty("label") String label,
            @JsonProperty("value") double value,
            @JsonProperty("tickets") long tickets
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeatBlockItem(@JsonProperty("label") String label, @JsonProperty("heat") double heat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DashEvent(@JsonProperty("title") String title, @JsonProperty("detail") String detail) {}
}
