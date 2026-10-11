package com.team22.eventticketing.booking.adapter;

import com.team22.eventticketing.booking.dto.BookingAnalyticsDTO;
import org.bson.Document;

public class MongoDocumentAdapter {

    public BookingAnalyticsDTO adaptToBookingAnalyticsDTO(Document doc) {

        long totalBookings = doc.getLong("totalBookings");
        long completedBookings = doc.getLong("completedBookings");
        long cancelledBookings = doc.getLong("cancelledBookings");

        double totalRevenue = doc.getDouble("totalRevenue");
        double avgBookingAmount = doc.getDouble("averageBookingAmount");
        double completionRate = doc.getDouble("completionRate");

//        return new BookingAnalyticsDTO(
//                totalBookings,
//                completedBookings,
//                cancelledBookings,
//                totalRevenue,
//                avgBookingAmount,
//                completionRate
//        );
        return BookingAnalyticsDTO.builder()
                .totalBookings(totalBookings)
                .completedBookings(completedBookings)
                .cancelledBookings(cancelledBookings)
                .totalRevenue(totalRevenue)
                .averageBookingAmount(avgBookingAmount)
                .completionRate(completionRate)
                .build();
    }
    public BookingAnalyticsDTO adapt(Document doc) {
        return adaptToBookingAnalyticsDTO(doc);
    }
}