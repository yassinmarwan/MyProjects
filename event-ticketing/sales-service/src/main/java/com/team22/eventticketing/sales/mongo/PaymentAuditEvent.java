package com.team22.eventticketing.sales.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "payment_audit_trail")
public class PaymentAuditEvent implements MongoEvent {

    @Id
    private String id;
    private Long saleId;
    private Instant timestamp;
    private String action;
    private Map<String, Object> details;
    private String method;
    private Double amount;

    public PaymentAuditEvent() {}

    public PaymentAuditEvent(String action, Map<String, Object> details, String method, Double amount) {
        this.timestamp = Instant.now();
        this.action = action;
        this.details = details;
        this.method = method;
        this.amount = amount;
    }

    public PaymentAuditEvent(Long saleId, String action, Map<String, Object> details, String method, Double amount) {
        this.timestamp = Instant.now();
        this.saleId = saleId;
        this.action = action;
        this.details = details;
        this.method = method;
        this.amount = amount;
    }

    @Override public String getId() { return id; }
    public Long getSaleId() { return saleId; }
    @Override public Instant getTimestamp() { return timestamp; }
    @Override public String getAction() { return action; }
    @Override public Map<String, Object> getDetails() { return details; }
    public String getMethod() { return method; }
    public Double getAmount() { return amount; }

    public void setId(String id) { this.id = id; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setAction(String action) { this.action = action; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
    public void setMethod(String method) { this.method = method; }
    public void setAmount(Double amount) { this.amount = amount; }
}
