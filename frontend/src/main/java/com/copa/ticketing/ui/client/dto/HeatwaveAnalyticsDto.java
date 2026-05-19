package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeatwaveAnalyticsDto(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("engine") String engine,
        @JsonProperty("summary") Summary summary,
        @JsonProperty("topMatches") List<TopMatch> topMatches,
        @JsonProperty("hostCountries") List<HostCountry> hostCountries,
        @JsonProperty("sectorDemand") List<SectorDemand> sectorDemand,
        @JsonProperty("paymentStatus") List<PaymentStatus> paymentStatus,
        @JsonProperty("heatmap") List<HeatBlock> heatmap,
        @JsonProperty("loadStatus") LoadStatus loadStatus
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            @JsonProperty("grossRevenue") double grossRevenue,
            @JsonProperty("ticketsIssued") long ticketsIssued,
            @JsonProperty("paidOrders") long paidOrders,
            @JsonProperty("activeReservations") long activeReservations,
            @JsonProperty("reservationsCreated") long reservationsCreated,
            @JsonProperty("convertedReservations") long convertedReservations,
            @JsonProperty("paymentPendingAmount") double paymentPendingAmount,
            @JsonProperty("conversionPercent") double conversionPercent,
            @JsonProperty("occupancyPercent") double occupancyPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopMatch(
            @JsonProperty("matchNumber") String matchNumber,
            @JsonProperty("homeTeam") String homeTeam,
            @JsonProperty("awayTeam") String awayTeam,
            @JsonProperty("venueName") String venueName,
            @JsonProperty("city") String city,
            @JsonProperty("country") String country,
            @JsonProperty("occupancyPercent") double occupancyPercent,
            @JsonProperty("grossRevenue") double grossRevenue,
            @JsonProperty("ticketsIssued") long ticketsIssued,
            @JsonProperty("paymentPendingOrders") long paymentPendingOrders,
            @JsonProperty("paymentPendingAmount") double paymentPendingAmount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HostCountry(
            @JsonProperty("country") String country,
            @JsonProperty("grossRevenue") double grossRevenue,
            @JsonProperty("ticketsIssued") long ticketsIssued,
            @JsonProperty("confirmedOccupancyPercent") double confirmedOccupancyPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectorDemand(
            @JsonProperty("sectorCode") String sectorCode,
            @JsonProperty("grossRevenue") double grossRevenue,
            @JsonProperty("ticketsIssued") long ticketsIssued,
            @JsonProperty("occupancyPercent") double occupancyPercent
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentStatus(
            @JsonProperty("paymentMethod") String paymentMethod,
            @JsonProperty("totalAmount") double totalAmount,
            @JsonProperty("paymentCount") long paymentCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeatBlock(
            @JsonProperty("sectorCode") String sectorCode,
            @JsonProperty("blockCode") String blockCode,
            @JsonProperty("heatPercent") double heatPercent,
            @JsonProperty("demandBand") String demandBand
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoadStatus(
            @JsonProperty("baseTables") long baseTables,
            @JsonProperty("loadedTables") long loadedTables
    ) {}
}
