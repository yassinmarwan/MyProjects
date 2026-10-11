package com.team22.eventticketing.booking.adapter; // Adjust package as needed

import com.team22.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import com.team22.eventticketing.booking.model.BookingStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BookingAnalyticsAdapter implements AnalyticsDashboardTarget {

    @Override
    public BookingAnalyticsDashboardDTO adaptRawData(List<Object[]> rawSqlResults) {
        Map<BookingStatus, Long> statusMap = new HashMap<>();
        long totalBookings = 0;
        double totalRevenue = 0;

        // The Core Adapter Logic: Translating Object[] to Java Types
        for (Object[] row : rawSqlResults) {
            BookingStatus status = BookingStatus.valueOf((String) row[0]);
            long count = ((Number) row[1]).longValue();
            double revenue = ((Number) row[2]).doubleValue();

            statusMap.put(status, count);
            totalBookings += count;
            totalRevenue += revenue;
        }

        // Calculate derived metrics
        long completed = statusMap.getOrDefault(BookingStatus.COMPLETED, 0L);
        long converted = statusMap.getOrDefault(BookingStatus.CONFIRMED, 0L) +
                statusMap.getOrDefault(BookingStatus.CHECKED_IN, 0L) +
                completed;

        double avgValue = completed > 0 ? totalRevenue / completed : 0.0;
        double convRate = totalBookings > 0 ? ((double) converted / totalBookings) * 100 : 0.0;

        // Use your Builder to return the clean DTO
        return BookingAnalyticsDashboardDTO.builder()
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .averageBookingValue(avgValue)
                .conversionRate(convRate)
                .bookingsByStatus(statusMap)
                .build();
    }
}