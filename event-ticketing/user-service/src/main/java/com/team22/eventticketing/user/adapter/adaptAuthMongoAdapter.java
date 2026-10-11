package com.team22.eventticketing.user.adapter;
import com.team22.eventticketing.user.dto.AuthEventDTO;

import org.bson.Document;

public class adaptAuthMongoAdapter {
    public AuthEventDTO adapt(Document doc) {
        AuthEventDTO dto = new AuthEventDTO();

        dto.setId(doc.getObjectId("_id") != null
                ? doc.getObjectId("_id").toString()
                : doc.getString("id"));

        dto.setTimestamp(doc.get("timestamp", java.time.Instant.class));
        dto.setAction(doc.getString("action"));
        dto.setDetails(doc.get("details", java.util.Map.class));
        dto.setUserId(doc.getLong("userId"));
        dto.setEmail(doc.getString("email"));

        return dto;

    }
}

