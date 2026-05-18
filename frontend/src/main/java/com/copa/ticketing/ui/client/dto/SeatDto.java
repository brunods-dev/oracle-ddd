package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatDto(
        @JsonProperty("venueSeatId")
        @JsonAlias({"venue_seat_id", "id"})
        long venueSeatId,
        long matchId,
        long matchSectorId,
        String sectorCode,
        String sectorName,
        String rowLabel,
        int seatNumber,
        String seatLabel,
        String blockCode,
        String entrance,
        double price,
        String status,
        boolean isOptimal
) {}
