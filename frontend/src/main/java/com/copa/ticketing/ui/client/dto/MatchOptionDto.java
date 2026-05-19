package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchOptionDto(
        @JsonProperty("matchId") long matchId,
        @JsonProperty("matchNumber") String matchNumber,
        @JsonProperty("groupName") String groupName,
        @JsonProperty("matchAt") String matchAt,
        @JsonProperty("matchStatus") String matchStatus,
        @JsonProperty("capacity") long capacity,
        @JsonProperty("homeTeam") String homeTeam,
        @JsonProperty("awayTeam") String awayTeam,
        @JsonProperty("venueName") String venueName,
        @JsonProperty("city") String city,
        @JsonProperty("country") String country,
        @JsonProperty("venueTimeZone") String venueTimeZone,
        @JsonProperty("totalQuantity") long totalQuantity,
        @JsonProperty("reservedQuantity") long reservedQuantity,
        @JsonProperty("soldQuantity") long soldQuantity,
        @JsonProperty("availableQuantity") long availableQuantity
) {}
