package com.team22.eventticketing.booking.adapter; // Adjust package as needed

import com.team22.eventticketing.booking.dto.BookingAnalyticsDashboardDTO;
import java.util.List;

public interface AnalyticsDashboardTarget {
    BookingAnalyticsDashboardDTO adaptRawData(List<Object[]> rawSqlResults);
}