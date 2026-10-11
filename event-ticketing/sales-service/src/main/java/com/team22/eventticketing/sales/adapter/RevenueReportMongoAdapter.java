package com.team22.eventticketing.sales.adapter;

import com.team22.eventticketing.sales.dto.RevenueReportDTO;
import org.bson.Document;

public class RevenueReportMongoAdapter {

    public RevenueReportDTO adapt(Document doc) {

        double totalRevenue = doc.getDouble("totalRevenue");
        int totalTransactions = doc.getInteger("totalTransactions");
        double avgSale = doc.getDouble("averageSale");
        double refunded = doc.getDouble("refundedAmount");
        int refundCount = doc.getInteger("refundCount");

        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTransactions)
                .averageSale(avgSale)
                .refundedAmount(refunded)
                .refundCount(refundCount)
                .build();
    }
}