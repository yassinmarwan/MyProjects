package com.team22.eventticketing.contracts.dto;

public record EventTicketSummaryDTO(long totalTicketsSold, long usedCount) {
    @Override
    public long totalTicketsSold() {
        return totalTicketsSold;
    }

    @Override
    public long usedCount() {
        return usedCount;
    }
}