package com.team22.eventticketing.sales.dto;

import static com.team22.eventticketing.sales.entity.TicketSale.PaymentMethod;

import static com.team22.eventticketing.sales.entity.TicketSale.SaleStatus;
import java.util.List;
import java.util.Map;

public class SaleDetailsDTO {

    private Long saleId;
    private Long bookingId;
    private Long userId;
    private Double originalAmount;
    private PaymentMethod method;
    private SaleStatus status;
    private Map<String, Object> transactionDetails;
    private List<AppliedPromotionDTO> appliedPromotions;
    private Double totalDiscount;
    private Double finalAmount;

//    public SaleDetailsDTO() {}
//
//    public SaleDetailsDTO(Long saleId, Long bookingId, Long userId, Double originalAmount,
//                          PaymentMethod method, SaleStatus status,
//                          Map<String, Object> transactionDetails,
//                          List<AppliedPromotionDTO> appliedPromotions,
//                          Double totalDiscount, Double finalAmount) {
//        this.saleId = saleId;
//        this.bookingId = bookingId;
//        this.userId = userId;
//        this.originalAmount = originalAmount;
//        this.method = method;
//        this.status = status;
//        this.transactionDetails = transactionDetails;
//        this.appliedPromotions = appliedPromotions;
//        this.totalDiscount = totalDiscount;
//        this.finalAmount = finalAmount;
//    }

    public SaleDetailsDTO(Builder builder){
        this.saleId = builder.saleId;
        this.bookingId = builder.bookingId;
        this.userId = builder.userId;
        this.originalAmount = builder.originalAmount;
        this.method = builder.method;
        this.status = builder.status;
        this.transactionDetails = builder.transactionDetails;
        this.appliedPromotions = builder.appliedPromotions;
        this.totalDiscount = builder.totalDiscount;
        this.finalAmount = builder.finalAmount;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long saleId;
        private Long bookingId;
        private Long userId;
        private Double originalAmount;
        private PaymentMethod method;
        private SaleStatus status;
        private Map<String, Object> transactionDetails;
        private List<AppliedPromotionDTO> appliedPromotions;
        private Double totalDiscount;
        private Double finalAmount;

        public Builder saleId(Long saleId){this.saleId=saleId;return this;}
        public Builder bookingId(Long bookingId){this.bookingId=bookingId;return this;}
        public Builder userId(Long userId){this.userId=userId;return this;}
        public Builder originalAmount(Double originalAmount){this.originalAmount=originalAmount;return this;}
        public Builder method(PaymentMethod method){this.method=method;return this;}
        public Builder status(SaleStatus status){this.status=status;return this;}
        public Builder transactionDetails(Map<String, Object> transactionDetails){this.transactionDetails=transactionDetails;return this;}
        public Builder appliedPromotions(List<AppliedPromotionDTO> appliedPromotions){this.appliedPromotions=appliedPromotions;return this;}
        public Builder totalDiscount(Double totalDiscount){this.totalDiscount=totalDiscount;return this;}
        public Builder finalAmount(Double finalAmount){this.finalAmount=finalAmount;return this;}


        public SaleDetailsDTO build() {
            return new SaleDetailsDTO(this);
        }
    }

    public Long getSaleId() { return saleId; }
    public void setSaleId(Long saleId) { this.saleId = saleId; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(Double originalAmount) { this.originalAmount = originalAmount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus status) { this.status = status; }

    public Map<String, Object> getTransactionDetails() { return transactionDetails; }
    public void setTransactionDetails(Map<String, Object> transactionDetails) { this.transactionDetails = transactionDetails; }

    public List<AppliedPromotionDTO> getAppliedPromotions() { return appliedPromotions; }
    public void setAppliedPromotions(List<AppliedPromotionDTO> appliedPromotions) { this.appliedPromotions = appliedPromotions; }

    public Double getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; }

    public Double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(Double finalAmount) { this.finalAmount = finalAmount; }
}
