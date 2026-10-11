package com.team22.eventticketing.contracts.dto;

public record VenueCoordsDTO(Double venueLat, Double venueLon) {
    @Override
    public Double venueLat() {
        return venueLat;
    }

    @Override
    public Double venueLon() {
        return venueLon;
    }
}