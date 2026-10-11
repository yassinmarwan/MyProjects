package com.team22.eventticketing.ticket.adapter;

import com.team22.eventticketing.ticket.model.dto.UnusedTicketDTO;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

public class PostgresRowAdapter {

    public UnusedTicketDTO adapt(Map<String, Object> row) {

        // Handle PostgreSQL date mapping safely
        Object dateObj = row.get("eventDate");
        LocalDateTime eventDate = null;
        if (dateObj instanceof Timestamp) {
            eventDate = ((Timestamp) dateObj).toLocalDateTime();
        } else if (dateObj instanceof LocalDateTime) {
            eventDate = (LocalDateTime) dateObj;
        }

        return UnusedTicketDTO.builder()
                .ticketId(((Number) row.get("ticketId")).longValue())
                .attendeeName((String) row.get("attendeeName"))
                .ticketCode((String) row.get("ticketCode"))
                .bookingId(((Number) row.get("bookingId")).longValue())
                .eventName((String) row.get("eventName"))
                .eventDate(eventDate)
                .build();
    }
}