package com.team22.eventticketing.event.dto;

import com.team22.eventticketing.event.model.EventSession;
import com.team22.eventticketing.event.model.EventStatus;
import java.util.List;

public record EventSessionAlertDTO(
        Long eventId,
        String eventName,
        EventStatus eventStatus,
        List<EventSession> unverifiedSessions,
        Integer unverifiedCount
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long eventId;
        private String eventName;
        private EventStatus eventStatus;
        private List<EventSession> unverifiedSessions;
        private Integer unverifiedCount;

        public Builder eventId(Long eventId) {
            this.eventId = eventId;
            return this;
        }
        public Builder eventName(String eventName) {
            this.eventName = eventName;
            return this;
        }
        public Builder eventStatus(EventStatus eventStatus) {
            this.eventStatus = eventStatus;
            return this;
        }
        public Builder unverifiedSessions(List<EventSession> unverifiedSessions) {
            this.unverifiedSessions = unverifiedSessions;
            return this;
        }
        public Builder unverifiedCount(Integer unverifiedCount) {
            this.unverifiedCount = unverifiedCount;
            return this;
        }

        // 4. The build method calls the record's canonical constructor
        public EventSessionAlertDTO build() {
            return new EventSessionAlertDTO(
                    this.eventId,
                    this.eventName,
                    this.eventStatus,
                    this.unverifiedSessions,
                    this.unverifiedCount
            );
        }
    }

}