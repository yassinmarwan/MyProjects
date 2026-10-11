package com.team22.eventticketing.booking.dto;

import java.util.List;
import java.util.Map;

public class BookingDetailsDTO {
    private Long bookingId;
    private Long userId;
    private Long eventId;
    private String status;
    private Double totalAmount;
    private Map<String, Object> metadata;
    private List<BookingItemDTO> items;
    private int totalItems;
    private long confirmedItems;

//    public BookingDetailsDTO(Long bookingId, Long userId, Long eventId,
//                             String status, Double totalAmount,
//                             Map<String, Object> metadata,
//                             List<BookingItemDTO> items,
//                             int totalItems, long confirmedItems) {
//        this.bookingId = bookingId;
//        this.userId = userId;
//        this.eventId = eventId;
//        this.status = status;
//        this.totalAmount = totalAmount;
//        this.metadata = metadata;
//        this.items = items;
//        this.totalItems = totalItems;
//        this.confirmedItems = confirmedItems;
//    }

    public BookingDetailsDTO(Builder builder){
        this.bookingId = builder.bookingId;
        this.userId = builder.userId;
        this.eventId = builder.eventId;
        this.status = builder.status;
        this.totalAmount = builder.totalAmount;
        this.metadata = builder.metadata;
        this.items = builder.items;
        this.totalItems = builder.totalItems;
        this.confirmedItems = builder.confirmedItems;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long bookingId;
        private Long userId;
        private Long eventId;
        private String status;
        private Double totalAmount;
        private Map<String, Object> metadata;
        private List<BookingItemDTO> items;
        private int totalItems;
        private long confirmedItems;

        public Builder bookingId(Long bookingId){this.bookingId=bookingId;return this;}
        public Builder userId(Long userId){this.userId=userId;return this;}
        public Builder eventId(Long eventId){this.eventId=eventId;return this;}
        public Builder status(String status){this.status=status;return this;}
        public Builder totalAmount(Double totalAmount){this.totalAmount=totalAmount;return this;}
        public Builder metadata(Map<String, Object> metadata){this.metadata=metadata;return this;}
        public Builder items(List<BookingItemDTO> items){this.items=items;return this;}
        public Builder totalItems(int totalItems){this.totalItems=totalItems;return this;}
        public Builder confirmedItems(long confirmedItems){this.confirmedItems=confirmedItems;return this;}


        public BookingDetailsDTO build() {
            return new BookingDetailsDTO(this);
        }

    }

    public Long getBookingId() { return bookingId; }
    public Long getUserId() { return userId; }
    public Long getEventId() { return eventId; }
    public String getStatus() { return status; }
    public Double getTotalAmount() { return totalAmount; }
    public Map<String, Object> getMetadata() { return metadata; }
    public List<BookingItemDTO> getItems() { return items; }
    public int getTotalItems() { return totalItems; }
    public long getConfirmedItems() { return confirmedItems; }
}