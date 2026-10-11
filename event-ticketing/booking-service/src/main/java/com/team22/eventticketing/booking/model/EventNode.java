package com.team22.eventticketing.booking.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("EventNode") // OR "Event" ?
public class EventNode {

    @Id
    private Long eventId;

    private String name;

    private String category;

    public EventNode() {
    }

    public EventNode(Long eventId, String name, String category) {
        this.eventId = eventId;
        this.name = name;
        this.category = category;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}