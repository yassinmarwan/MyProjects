package com.team22.eventticketing.booking.adapter;

import com.team22.eventticketing.booking.dto.EventRecommendationDTO;
import org.neo4j.driver.Record;

public class Neo4jRecordAdapter {

    public EventRecommendationDTO adapt(Record record) {

        EventRecommendationDTO dto = new EventRecommendationDTO();
        dto.setEventId(record.get("eventId").asLong());
        dto.setScore(record.get("score").asLong());

        return dto;
    }
}