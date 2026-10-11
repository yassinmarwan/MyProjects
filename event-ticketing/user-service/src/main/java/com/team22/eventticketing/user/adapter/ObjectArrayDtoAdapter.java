package com.team22.eventticketing.user.adapter;

import com.team22.eventticketing.user.dto.UserBookingSummaryDTO;

public class ObjectArrayDtoAdapter {

    public UserBookingSummaryDTO adapt(Object[] row) {

        return UserBookingSummaryDTO.builder()
                .userId((Long) row[0])
                .name((String) row[1])
                .totalBookings((Long) row[2])
                .completedBookings((Long) row[3])
                .cancelledBookings((Long) row[4])
                .totalSpent((Double) row[5])
                .averageBookingAmount((Double) row[6])
                .build();
    }
}