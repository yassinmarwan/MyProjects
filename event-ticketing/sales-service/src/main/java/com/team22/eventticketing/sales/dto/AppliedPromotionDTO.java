package com.team22.eventticketing.sales.dto;

import static com.team22.eventticketing.sales.entity.Promotion.DiscountType;
import java.time.LocalDateTime;

public class AppliedPromotionDTO {

    private String promotionCode;
    private DiscountType discountType;
    private Double discountApplied;
    private LocalDateTime appliedAt;

    public AppliedPromotionDTO() {}

    public AppliedPromotionDTO(String promotionCode, DiscountType discountType,
                               Double discountApplied, LocalDateTime appliedAt) {
        this.promotionCode = promotionCode;
        this.discountType = discountType;
        this.discountApplied = discountApplied;
        this.appliedAt = appliedAt;
    }

    public String getPromotionCode() { return promotionCode; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }

    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

    public Double getDiscountApplied() { return discountApplied; }
    public void setDiscountApplied(Double discountApplied) { this.discountApplied = discountApplied; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
