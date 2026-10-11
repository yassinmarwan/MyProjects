package com.team22.eventticketing.ticket.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team22.eventticketing.ticket.model.dto.EventAttendanceSummaryDTO;
import java.util.Map;
import com.team22.eventticketing.ticket.model.dto.TicketScanDTO;
import com.team22.eventticketing.ticket.model.TicketScanEvent;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class CassandraRowAdapter {

public EventAttendanceSummaryDTO adapt(Map<String, Object> row) {

                return EventAttendanceSummaryDTO.builder()
                                .eventId(((Number) row.get("eventId")).longValue())
                                .totalTickets(((Number) row.get("total")).longValue())
                                .usedTickets(((Number) row.get("used")).longValue())
                                .validTickets(((Number) row.get("valid")).longValue())
                                .lastCheckIn((java.time.LocalDateTime) row.get("last_checkin"))
                                .build();
        }

    public TicketScanDTO adapt(TicketScanEvent event) {
        return TicketScanDTO.builder().timestamp(event.getKey().getTimestamp())
                .scanType(event.getScanType())
                .attendeeName(event.getAttendeeName())
                .gate(event.getGate())
                .section(event.getSection())
                .seatNumber(event.getSeatNumber())
                .notes(event.getNotes())
                .build();
    }
}