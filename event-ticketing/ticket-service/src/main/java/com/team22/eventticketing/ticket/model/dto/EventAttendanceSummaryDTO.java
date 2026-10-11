package com.team22.eventticketing.ticket.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class EventAttendanceSummaryDTO {
    private Long eventId;
    private long totalTickets;
    private long usedTickets;
    private long validTickets;
    private double attendanceRate;
    private LocalDateTime lastCheckIn;

//    public EventAttendanceSummaryDTO(Long eventId, long total, long used, long valid, LocalDateTime last) {
//        this.eventId = eventId;
//        this.totalTickets = total;
//        this.usedTickets = used;
//        this.validTickets = valid;
//        this.lastCheckIn = last;
//        // Calculate rate: (Used / Total) * 100
//        this.attendanceRate = (total > 0) ? ((double) used / total) * 100 : 0.0;
//    }

    public EventAttendanceSummaryDTO(Builder builder){
        this.eventId = builder.eventId;
        this.totalTickets = builder.totalTickets;
        this.usedTickets = builder.usedTickets;
        this.validTickets = builder.validTickets;
        this.lastCheckIn = builder.lastCheckIn;
        // Calculate rate: (Used / Total) * 100
        this.attendanceRate = (builder.totalTickets > 0) ? ((double) builder.usedTickets / builder.totalTickets) * 100 : 0.0;

    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long eventId;
        private long totalTickets;
        private long usedTickets;
        private long validTickets;
        private double attendanceRate;
        private LocalDateTime lastCheckIn;

        public Builder eventId(Long eventId){this.eventId=eventId;return this;}
        public Builder totalTickets(Long totalTickets){this.totalTickets=totalTickets;return this;}
        public Builder usedTickets(Long usedTickets){this.usedTickets=usedTickets;return this;}
        public Builder validTickets(Long validTickets){this.validTickets=validTickets;return this;}
        public Builder lastCheckIn(LocalDateTime lastCheckIn){this.lastCheckIn=lastCheckIn;return this;}


        public EventAttendanceSummaryDTO build() {
            return new EventAttendanceSummaryDTO(this);
        }

    }

    public Long getEventId() {
        return eventId;
    }

    public long getTotalTickets() {
        return totalTickets;
    }

    public long getUsedTickets() {
        return usedTickets;
    }

    public long getValidTickets() {
        return validTickets;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public LocalDateTime getLastCheckIn() {
        return lastCheckIn;
    }

}