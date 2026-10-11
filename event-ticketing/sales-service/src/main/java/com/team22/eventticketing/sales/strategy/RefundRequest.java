package com.team22.eventticketing.sales.strategy;

public class RefundRequest {
    private String reason;

    public RefundRequest() {}
    public RefundRequest(String reason) { this.reason = reason; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}