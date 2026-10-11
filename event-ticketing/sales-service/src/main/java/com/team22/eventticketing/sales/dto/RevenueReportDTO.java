package com.team22.eventticketing.sales.dto;

import java.util.Map;

public class RevenueReportDTO {
    private double totalRevenue;
    private int totalTransactions;
    private double averageSale;
    private double refundedAmount;
    private int refundCount;

//    public RevenueReportDTO(double totalRevenue, int totalTransactions, double averageSale,
//                            double refundedAmount, int refundCount) {
//        this.totalRevenue = totalRevenue;
//        this.totalTransactions = totalTransactions;
//        this.averageSale = averageSale;
//        this.refundedAmount = refundedAmount;
//        this.refundCount = refundCount;
//    }

    public RevenueReportDTO(Builder builder){
        this.totalRevenue = builder.totalRevenue;
        this.totalTransactions = builder.totalTransactions;
        this.averageSale = builder.averageSale;
        this.refundedAmount = builder.refundedAmount;
        this.refundCount = builder.refundCount;
    }

    public static Builder builder(){return new RevenueReportDTO.Builder();}

    public static class Builder{
        private double totalRevenue;
        private int totalTransactions;
        private double averageSale;
        private double refundedAmount;
        private int refundCount;

        public Builder totalRevenue(double totalRevenue){this.totalRevenue=totalRevenue;return this;}
        public Builder totalTransactions(int totalTransactions){this.totalTransactions=totalTransactions;return this;}
        public Builder averageSale(double averageSale){this.averageSale=averageSale;return this;}
        public Builder refundedAmount(double refundedAmount){this.refundedAmount=refundedAmount;return this;}
        public Builder refundCount(int refundCount){this.refundCount=refundCount;return this;}

        public RevenueReportDTO build() {
            return new RevenueReportDTO(this);
        }
    }


    // Getters & setters
    public double getTotalRevenue() { return totalRevenue; }
    public int getTotalTransactions() { return totalTransactions; }
    public double getAverageSale() { return averageSale; }
    public double getRefundedAmount() { return refundedAmount; }
    public int getRefundCount() { return refundCount; }
}