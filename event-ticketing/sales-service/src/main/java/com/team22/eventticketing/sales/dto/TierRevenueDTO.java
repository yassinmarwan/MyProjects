package com.team22.eventticketing.sales.dto;

public class TierRevenueDTO {

    private String tier;
    private double totalRevenue;
    private long saleCount;
    private long ticketsSold;
    private double averageRevenuePerSale;

    public TierRevenueDTO() {}

    public TierRevenueDTO(Builder builder) {
        this.tier = builder.tier;
        this.totalRevenue = builder.totalRevenue;
        this.saleCount = builder.saleCount;
        this.ticketsSold = builder.ticketsSold;
        this.averageRevenuePerSale = builder.averageRevenuePerSale;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String tier;
        private double totalRevenue;
        private long saleCount;
        private long ticketsSold;
        private double averageRevenuePerSale;

        public Builder tier(String tier) { this.tier = tier; return this; }
        public Builder totalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder saleCount(long saleCount) { this.saleCount = saleCount; return this; }
        public Builder ticketsSold(long ticketsSold) { this.ticketsSold = ticketsSold; return this; }
        public Builder averageRevenuePerSale(double averageRevenuePerSale) { this.averageRevenuePerSale = averageRevenuePerSale; return this; }

        public TierRevenueDTO build() { return new TierRevenueDTO(this); }
    }

    public String getTier() { return tier; }
    public double getTotalRevenue() { return totalRevenue; }
    public long getSaleCount() { return saleCount; }
    public long getTicketsSold() { return ticketsSold; }
    public double getAverageRevenuePerSale() { return averageRevenuePerSale; }

    public void setTier(String tier) { this.tier = tier; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
    public void setSaleCount(long saleCount) { this.saleCount = saleCount; }
    public void setTicketsSold(long ticketsSold) { this.ticketsSold = ticketsSold; }
    public void setAverageRevenuePerSale(double averageRevenuePerSale) { this.averageRevenuePerSale = averageRevenuePerSale; }
}
