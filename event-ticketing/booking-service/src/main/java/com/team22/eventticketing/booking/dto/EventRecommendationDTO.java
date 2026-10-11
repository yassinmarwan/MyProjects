package com.team22.eventticketing.booking.dto;

import java.time.LocalDateTime;

public class EventRecommendationDTO {

    private Long eventId;
    private String name;
    private String category;
    private LocalDateTime eventDate;
    private Long score;

    public EventRecommendationDTO() {
    }

    public EventRecommendationDTO(Long eventId,
                                  String name,
                                  String category,
                                  LocalDateTime eventDate,
                                  Long score) {
        this.eventId = eventId;
        this.name = name;
        this.category = category;
        this.eventDate = eventDate;
        this.score = score;
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

    public String getCategory() {
        return category;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public Long getScore() {
        return score;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public void setScore(Long score) {
        this.score = score;
    }
}