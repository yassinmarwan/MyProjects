package com.team22.eventticketing.booking.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.Set;

@Node("UserNode") // OR "Event" ?
public class UserNode {

    @Id
    private Long userId;

    private String name;

    @Relationship(type = "ATTENDED")
    private Set<AttendedRelationship> attendedEvents;

    public UserNode() {
    }

    public UserNode(Long userId, String name, Set<AttendedRelationship> attendedEvents) {
        this.userId = userId;
        this.name = name;
        this.attendedEvents = attendedEvents;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<AttendedRelationship> getAttendedEvents() {
        return attendedEvents;
    }

    public void setAttendedEvents(Set<AttendedRelationship> attendedEvents) {
        this.attendedEvents = attendedEvents;
    }
}