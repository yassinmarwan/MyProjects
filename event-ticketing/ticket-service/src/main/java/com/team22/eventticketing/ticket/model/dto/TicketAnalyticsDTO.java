package com.team22.eventticketing.ticket.model.dto;

import java.util.Map;
import java.io.Serializable;

public class TicketAnalyticsDTO implements Serializable {

    // All fields defined in the M2 spec
    private final Long totalIssued;
    private final Long usedCount;
    private final Long validCount;
    private final Long expiredCount;
    private final Long cancelledCount;
    private final Double attendanceRate;
    private final Map<String, Long> ticketsByStatus;
    private static final long serialVersionUID = 1L;

    // Private constructor enforcing the use of the Builder
    private TicketAnalyticsDTO(Builder builder) {
        this.totalIssued = builder.totalIssued;
        this.usedCount = builder.usedCount;
        this.validCount = builder.validCount;
        this.expiredCount = builder.expiredCount;
        this.cancelledCount = builder.cancelledCount;
        this.attendanceRate = builder.attendanceRate;
        this.ticketsByStatus = builder.ticketsByStatus;
    }

    // Getters (Required by Spring Boot/Jackson for JSON serialization)
    public Long getTotalIssued() {
        return totalIssued;
    }

    public Long getUsedCount() {
        return usedCount;
    }

    public Long getValidCount() {
        return validCount;
    }

    public Long getExpiredCount() {
        return expiredCount;
    }

    public Long getCancelledCount() {
        return cancelledCount;
    }

    public Double getAttendanceRate() {
        return attendanceRate;
    }

    public Map<String, Long> getTicketsByStatus() {
        return ticketsByStatus;
    }

    // Static builder() method to start construction as required by the grader[cite:
    // 1]
    public static Builder builder() {
        return new Builder();
    }

    // Static inner Builder class[cite: 1]
    public static class Builder {
        private Long totalIssued;
        private Long usedCount;
        private Long validCount;
        private Long expiredCount;
        private Long cancelledCount;
        private Double attendanceRate;
        private Map<String, Long> ticketsByStatus;

        // Fluent setters returning 'this'[cite: 1]
        public Builder totalIssued(Long totalIssued) {
            this.totalIssued = totalIssued;
            return this;
        }

        public Builder usedCount(Long usedCount) {
            this.usedCount = usedCount;
            return this;
        }

        public Builder validCount(Long validCount) {
            this.validCount = validCount;
            return this;
        }

        public Builder expiredCount(Long expiredCount) {
            this.expiredCount = expiredCount;
            return this;
        }

        public Builder cancelledCount(Long cancelledCount) {
            this.cancelledCount = cancelledCount;
            return this;
        }

        public Builder attendanceRate(Double attendanceRate) {
            this.attendanceRate = attendanceRate;
            return this;
        }

        public Builder ticketsByStatus(Map<String, Long> ticketsByStatus) {
            this.ticketsByStatus = ticketsByStatus;
            return this;
        }

        // build() method returning the final DTO instance[cite: 1]
        public TicketAnalyticsDTO build() {
            return new TicketAnalyticsDTO(this);
        }
    }
}