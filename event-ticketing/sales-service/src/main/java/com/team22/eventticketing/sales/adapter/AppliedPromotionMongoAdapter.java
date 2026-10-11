package com.team22.eventticketing.sales.adapter;

import com.team22.eventticketing.sales.dto.AppliedPromotionDTO;
import com.team22.eventticketing.sales.entity.*;
import org.bson.Document;

import java.time.LocalDateTime;

public class AppliedPromotionMongoAdapter {

    public AppliedPromotionDTO adapt(Document doc) {

        AppliedPromotionDTO dto = new AppliedPromotionDTO();

        dto.setPromotionCode(doc.getString("promotionCode"));

        dto.setDiscountType(
                Promotion.DiscountType.valueOf(doc.getString("discountType"))
        );

        dto.setDiscountApplied(doc.getDouble("discountApplied"));

        dto.setAppliedAt(
                doc.get("appliedAt", LocalDateTime.class)
        );

        return dto;
    }
}