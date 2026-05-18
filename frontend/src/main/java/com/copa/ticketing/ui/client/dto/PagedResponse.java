package com.copa.ticketing.ui.client.dto;

import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int size, long total) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
