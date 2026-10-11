package com.team22.eventticketing.booking.dto;

public class BookingAnalyticsDTO {
    private long totalBookings;
    private long completedBookings;
    private long cancelledBookings;
    private double totalRevenue;
    private double averageBookingAmount;
    private double completionRate;

//    public BookingAnalyticsDTO(long totalBookings, long completedBookings,
//                               long cancelledBookings, double totalRevenue,
//                               double averageBookingAmount, double completionRate) {
//        this.totalBookings = totalBookings;
//        this.completedBookings = completedBookings;
//        this.cancelledBookings = cancelledBookings;
//        this.totalRevenue = totalRevenue;
//        this.averageBookingAmount = averageBookingAmount;
//        this.completionRate = completionRate;
//    }

    public BookingAnalyticsDTO(Builder builder){
        this.totalBookings = builder.totalBookings;
        this.completedBookings = builder.completedBookings;
        this.cancelledBookings = builder.cancelledBookings;
        this.totalRevenue = builder.totalRevenue;
        this.averageBookingAmount = builder.averageBookingAmount;
        this.completionRate = builder.completionRate;
    }

    public static Builder builder(){return new Builder();}

    public static class Builder{
        private long totalBookings;
        private long completedBookings;
        private long cancelledBookings;
        private double totalRevenue;
        private double averageBookingAmount;
        private double completionRate;

        public Builder totalBookings(long totalBookings){this.totalBookings=totalBookings;return this;}
        public Builder completedBookings(long completedBookings){this.completedBookings=completedBookings;return this;}
        public Builder cancelledBookings(long cancelledBookings){this.cancelledBookings=cancelledBookings;return this;}
        public Builder totalRevenue(double totalRevenue){this.totalRevenue=totalRevenue;return this;}
        public Builder averageBookingAmount(double averageBookingAmount){this.averageBookingAmount=averageBookingAmount;return this;}
        public Builder completionRate(double completionRate){this.completionRate=completionRate;return this;}

        public BookingAnalyticsDTO build() {
            return new BookingAnalyticsDTO(this);
        }
    }


    public long getTotalBookings() { return totalBookings; }
    public long getCompletedBookings() { return completedBookings; }
    public long getCancelledBookings() { return cancelledBookings; }
    public double getTotalRevenue() { return totalRevenue; }
    public double getAverageBookingAmount() { return averageBookingAmount; }
    public double getCompletionRate() { return completionRate; }
}