package com.team22.eventticketing.user.adapter;

import com.team22.eventticketing.user.dto.UserBookingSummaryDTO;
import org.bson.Document;

public class UserBookingSummaryMongoAdapter {
    public UserBookingSummaryDTO adapt(Document doc) {

        return UserBookingSummaryDTO.builder()
                .userId(doc.getLong("userId"))
                .name(doc.getString("name"))
                .totalBookings(doc.getLong("totalBookings"))
                .completedBookings(doc.getLong("completedBookings"))
                .cancelledBookings(doc.getLong("cancelledBookings"))
                .totalSpent(doc.getDouble("totalSpent"))
                .averageBookingAmount(doc.getDouble("averageBookingAmount"))
                .build();
    }
}
