package com.team22.eventticketing.user.adapter;

import com.team22.eventticketing.user.dto.TopAttendeeDTO;
import org.bson.Document;

public class TopAttendeeMongoAdapter {
    public TopAttendeeDTO adapt(Document doc) {

        return TopAttendeeDTO.builder()
                .userId(doc.getLong("userId"))
                .name(doc.getString("name"))
                .totalSpent(doc.getDouble("totalSpent"))
                .bookingCount(doc.getLong("bookingCount"))
                .build();
    }
}

