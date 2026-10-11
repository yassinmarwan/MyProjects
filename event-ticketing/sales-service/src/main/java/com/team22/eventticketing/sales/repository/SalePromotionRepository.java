package com.team22.eventticketing.sales.repository;

import com.team22.eventticketing.sales.entity.SalePromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SalePromotionRepository extends JpaRepository<SalePromotion, Long> {

    // Get all promotions applied to a sale
    List<SalePromotion> findByTicketSaleId(Long ticketSaleId);

    // Check if a promotion is already applied to a sale (S5-F5)
    boolean existsByTicketSaleIdAndPromotionId(Long ticketSaleId, Long promotionId);
}