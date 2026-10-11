package com.team22.eventticketing.ticket.adapter;

import com.team22.eventticketing.ticket.model.dto.NearbyTicketDTO;

public class ObjectArrayDtoAdapter {

    public NearbyTicketDTO adapt(Object[] r) {

        return NearbyTicketDTO.builder()
                .ticketId(((Number) r[0]).longValue())
                .attendeeName((String) r[1])
                .bookingId(((Number) r[2]).longValue())
                .eventName((String) r[3])
                .eventLat(r[4] != null ? ((Number) r[4]).doubleValue() : null)
                .eventLon(r[5] != null ? ((Number) r[5]).doubleValue() : null)
                .distanceKm(((Number) r[6]).doubleValue())
                .build();
    }
}