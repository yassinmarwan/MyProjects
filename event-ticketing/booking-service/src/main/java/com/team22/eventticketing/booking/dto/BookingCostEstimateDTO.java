package com.team22.eventticketing.booking.dto;

public class BookingCostEstimateDTO {
    private Double ticketCost;
    private Double serviceFee;
    private Double estimatedTotal;
    private Double demandMultiplier;

//    public BookingCostEstimateDTO(Double ticketCost, Double serviceFee, Double estimatedTotal, Double demandMultiplier) {
//        this.ticketCost = ticketCost;
//        this.serviceFee = serviceFee;
//        this.estimatedTotal = estimatedTotal;
//        this.demandMultiplier = demandMultiplier;
//    }

    public BookingCostEstimateDTO(Builder builder){
        this.ticketCost = builder.ticketCost;
        this.serviceFee = builder.serviceFee;
        this.estimatedTotal = builder.estimatedTotal;
        this.demandMultiplier = builder.demandMultiplier;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Double ticketCost;
        private Double serviceFee;
        private Double estimatedTotal;
        private Double demandMultiplier;

        public Builder ticketCost(Double ticketCost){this.ticketCost=ticketCost;return this;}
        public Builder serviceFee(Double serviceFee){this.serviceFee=serviceFee;return this;}
        public Builder estimatedTotal(Double estimatedTotal){this.estimatedTotal=estimatedTotal;return this;}
        public Builder demandMultiplier(Double demandMultiplier){this.demandMultiplier=demandMultiplier;return this;}

        public BookingCostEstimateDTO build() {
            return new BookingCostEstimateDTO(this);
        }

    }

    public Double getDemandMultiplier() {
        return demandMultiplier;
    }

    public void setDemandMultiplier(Double demandMultiplier) {
        this.demandMultiplier = demandMultiplier;
    }

    public Double getEstimatedTotal() {
        return estimatedTotal;
    }

    public void setEstimatedTotal(Double estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
    }

    public Double getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(Double serviceFee) {
        this.serviceFee = serviceFee;
    }

    public Double getTicketCost() {
        return ticketCost;
    }

    public void setTicketCost(Double ticketCost) {
        this.ticketCost = ticketCost;
    }
}