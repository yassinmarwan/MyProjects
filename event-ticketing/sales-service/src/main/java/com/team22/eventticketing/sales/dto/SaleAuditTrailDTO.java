package com.team22.eventticketing.sales.dto;

import java.util.List;

public class SaleAuditTrailDTO {

    private Long saleId;
    private List<AuditEventDTO> events;

    public SaleAuditTrailDTO() {}

    public SaleAuditTrailDTO(Long saleId, List<AuditEventDTO> events) {
        this.saleId = saleId;
        this.events = events;
    }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public List<AuditEventDTO> getEvents() { return events; }
    public void setEvents(List<AuditEventDTO> events) { this.events = events; }
}
