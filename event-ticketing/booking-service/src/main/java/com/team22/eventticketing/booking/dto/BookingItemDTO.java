package com.team22.eventticketing.booking.dto;

import java.util.Map;

public class BookingItemDTO {
    private Long id;
    private Integer eventOrder;
    private String sessionTitle;
    private Integer quantity;
    private Double unitPrice;
    private String status;
    private Map<String, Object> metadata;

    public BookingItemDTO(Long id, Integer eventOrder, String sessionTitle,
                          Integer quantity, Double unitPrice, String status,
                          Map<String, Object> metadata) {
        this.id = id;
        this.eventOrder = eventOrder;
        this.sessionTitle = sessionTitle;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = status;
        this.metadata = metadata;
    }

    public Long getId() { return id; }
    public Integer getEventOrder() { return eventOrder; }
    public String getSessionTitle() { return sessionTitle; }
    public Integer getQuantity() { return quantity; }
    public Double getUnitPrice() { return unitPrice; }
    public String getStatus() { return status; }
    public Map<String, Object> getMetadata() { return metadata; }
}