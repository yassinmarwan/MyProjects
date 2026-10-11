package com.team22.eventticketing.event.adapter;

import com.team22.eventticketing.event.dto.EventDashboardDTO;
import com.team22.eventticketing.event.dto.EventRevenueDTO;
import org.bson.Document;


public class MongoDocumentAdapter {

    public EventRevenueDTO adaptToEventRevenueDTO(Document doc) {

        return EventRevenueDTO.builder()
                .eventId(doc.getLong("eventId"))
                .name(doc.getString("name"))
                .totalBookings(doc.getLong("totalBookings"))
                .totalRevenue(doc.getDouble("totalRevenue"))
                .averageBookingAmount(doc.getDouble("averageBookingAmount"))
                .build();
    }
    public EventRevenueDTO adapt(Document doc) {
        return adaptToEventRevenueDTO(doc);
    }

    public EventDashboardDTO adaptToEventDashboardDTO(Document doc) {

        return EventDashboardDTO.builder()
                .eventId(doc.getLong("eventId"))
                .name(doc.getString("name"))
                .totalBookings(doc.getLong("totalBookings"))
                .totalTicketsSold(doc.getLong("totalTicketsSold"))
                .totalRevenue(doc.getDouble("totalRevenue"))
                .averageAttendanceRate(doc.getDouble("averageAttendanceRate"))
                .averageRating(doc.getDouble("averageRating"))
                .build();
    }
}