package com.copa.ticketing.ui.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SectorDto(
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
) {}
