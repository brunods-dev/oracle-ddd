package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SelloutStatusDto(
        @JsonProperty("matchId") long matchId,
        @JsonProperty("matchNumber") String matchNumber,
        @JsonProperty("matchStatus") String matchStatus,
        @JsonProperty("capacity") long capacity,
        @JsonProperty("homeTeam") String homeTeam,
        @JsonProperty("awayTeam") String awayTeam,
        @JsonProperty("venueName") String venueName,
        @JsonProperty("city") String city,
        @JsonProperty("country") String country,
        @JsonProperty("sectors") List<SectorStatus> sectors,
        @JsonProperty("totals") Totals totals,
        @JsonProperty("statusMix") StatusMix statusMix,
        @JsonProperty("paidOrders") long paidOrders,
        @JsonProperty("revenue") double revenue,
        @JsonProperty("running") boolean running,
        @JsonProperty("activeMatchNumber") Integer activeMatchNumber,
        @JsonProperty("progressPercent") double progressPercent,
        @JsonProperty("events") List<SelloutEventDto> events
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectorStatus(
            @JsonProperty("sectorCode") String sectorCode,
            @JsonProperty("totalQuantity") long totalQuantity,
            @JsonProperty("reservedQuantity") long reservedQuantity,
            @JsonProperty("soldQuantity") long soldQuantity,
            @JsonProperty("availableQuantity") long availableQuantity,
            @JsonProperty("status") String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            @JsonProperty("total") long total,
            @JsonProperty("reserved") long reserved,
            @JsonProperty("sold") long sold,
            @JsonProperty("available") long available
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusMix(
            @JsonProperty("reservedOnlySeats") long reservedOnlySeats,
            @JsonProperty("paymentPendingSeats") long paymentPendingSeats,
            @JsonProperty("issuedSeats") long issuedSeats,
            @JsonProperty("reservedOnlyReservations") long reservedOnlyReservations,
            @JsonProperty("paymentPendingOrders") long paymentPendingOrders,
            @JsonProperty("paidOrders") long paidOrders
    ) {}
}
