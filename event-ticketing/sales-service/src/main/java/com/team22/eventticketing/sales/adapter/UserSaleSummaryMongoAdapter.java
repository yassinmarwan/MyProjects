package com.team22.eventticketing.sales.adapter;

import com.team22.eventticketing.sales.dto.UserSaleSummaryDTO;
import org.bson.Document;

import java.util.Map;

public class UserSaleSummaryMongoAdapter {
    public UserSaleSummaryDTO adaptToUserSaleSummaryDTO(Document doc) {

        return UserSaleSummaryDTO.builder()
                .userId(doc.getLong("userId"))
                .totalSales(doc.getLong("totalSales"))
                .totalAmount(doc.getDouble("totalAmount"))
                .methodBreakdown((Map<String, Double>) doc.get("methodBreakdown"))
                .build();
    }
}
