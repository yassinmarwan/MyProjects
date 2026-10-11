package com.team22.eventticketing.ticket.adapter;

import com.team22.eventticketing.ticket.model.dto.UnusedTicketDTO;
import org.bson.Document;
import java.time.LocalDateTime;

public class MongoAdapter {
    public UnusedTicketDTO adapt(Document doc) {

        return UnusedTicketDTO.builder()
                .ticketId(doc.getLong("ticketId"))
                .attendeeName(doc.getString("attendeeName"))
                .ticketCode(doc.getString("ticketCode"))
                .bookingId(doc.getLong("bookingId"))
                .eventName(doc.getString("eventName"))
                .eventDate(doc.get("eventDate", LocalDateTime.class))
                .build();
    }
}
