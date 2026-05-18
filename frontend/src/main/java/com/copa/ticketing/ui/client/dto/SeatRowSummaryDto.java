package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatRowSummaryDto(
        String rowLabel,
        long totalSeats,
        long availableCount,
        long reservedCount,
        long soldCount
) {}
