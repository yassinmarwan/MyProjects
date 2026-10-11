package com.team22.eventticketing.sales.adapter;

import com.team22.eventticketing.sales.dto.PromotionUsageDTO;
import com.team22.eventticketing.sales.entity.Promotion;
import com.team22.eventticketing.sales.entity.Promotion.DiscountType;
import org.bson.Document;

public class PromotionUsageMongoAdapter {
    public PromotionUsageDTO adapt(Document doc) {
        return PromotionUsageDTO.builder()
                .promotionId(doc.getLong("promotionId"))
                .code(doc.getString("code"))
                .discountType(Promotion.DiscountType.valueOf(doc.getString("discountType")))
                .discountValue(doc.getDouble("discountValue"))
                .timesUsed(doc.getInteger("timesUsed"))
                .totalDiscountGiven(doc.getDouble("totalDiscountGiven"))
                .active(doc.getBoolean("active"))
                .expired(doc.getBoolean("expired"))
                .build();
    }
}
