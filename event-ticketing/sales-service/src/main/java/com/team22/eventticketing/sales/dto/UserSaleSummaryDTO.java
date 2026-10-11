package com.team22.eventticketing.sales.dto;

import java.util.Map;

public class UserSaleSummaryDTO {

    private Long userId;
    private Long totalSales;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

//    public UserSaleSummaryDTO() {}
//
//    public UserSaleSummaryDTO(Long userId, Long totalSales, Double totalAmount,
//                              Map<String, Double> methodBreakdown) {
//        this.userId = userId;
//        this.totalSales = totalSales;
//        this.totalAmount = totalAmount;
//        this.methodBreakdown = methodBreakdown;
//    }

    public UserSaleSummaryDTO(Builder builder){
        this.userId = builder.userId;
        this.totalSales = builder.totalSales;
        this.totalAmount = builder.totalAmount;
        this.methodBreakdown = builder.methodBreakdown;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private Long userId;
        private Long totalSales;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(long userId){this.userId=userId;return this;}
        public Builder totalSales(long totalSales){this.totalSales=totalSales;return this;}
        public Builder totalAmount(double totalAmount){this.totalAmount=totalAmount;return this;}
        public Builder methodBreakdown(Map<String, Double> methodBreakdown){this.methodBreakdown=methodBreakdown;return this;}

        public UserSaleSummaryDTO build() {
            return new UserSaleSummaryDTO(this);
        }
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTotalSales() { return totalSales; }
    public void setTotalSales(Long totalSales) { this.totalSales = totalSales; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }
}
