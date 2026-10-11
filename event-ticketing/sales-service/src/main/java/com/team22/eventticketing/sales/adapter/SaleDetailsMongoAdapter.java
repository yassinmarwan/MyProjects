package com.team22.eventticketing.sales.adapter;

import com.team22.eventticketing.sales.dto.AppliedPromotionDTO;
import com.team22.eventticketing.sales.dto.SaleDetailsDTO;
import com.team22.eventticketing.sales.entity.*;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SaleDetailsMongoAdapter {

    private final AppliedPromotionMongoAdapter promotionAdapter =
            new AppliedPromotionMongoAdapter();

    public SaleDetailsDTO adapt(Document doc) {

        List<Document> promotionsDocs =
                (List<Document>) doc.get("appliedPromotions");

        List<AppliedPromotionDTO> appliedPromotions = new ArrayList<>();

        if (promotionsDocs != null) {
            for (Document p : promotionsDocs) {
                appliedPromotions.add(promotionAdapter.adapt(p));
            }
        }

        return SaleDetailsDTO.builder()
                .saleId(doc.getLong("saleId"))
                .bookingId(doc.getLong("bookingId"))
                .userId(doc.getLong("userId"))
                .originalAmount(doc.getDouble("originalAmount"))
                .method(TicketSale.PaymentMethod.valueOf(doc.getString("method")))
                .status(TicketSale.SaleStatus.valueOf(doc.getString("status")))
                .transactionDetails((Map<String, Object>) doc.get("transactionDetails"))
                .appliedPromotions(appliedPromotions)
                .totalDiscount(doc.getDouble("totalDiscount"))
                .finalAmount(doc.getDouble("finalAmount"))
                .build();
    }
}