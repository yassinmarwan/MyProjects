package com.team22.eventticketing.sales.repository;

import com.team22.eventticketing.sales.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    // Find promotion by unique code
    Optional<Promotion> findByCode(String code);

    // [S5-F9] Top used promotions analytics
    @Query(value = """
            SELECT p.id, p.code, p.discount_type, p.discount_value,
                   p.current_uses, COALESCE(SUM(sp.discount_applied), 0) as totalDiscountGiven,
                   p.active, p.expiry_date
            FROM promotions p
            LEFT JOIN sale_promotions sp ON p.id = sp.promotion_id
            GROUP BY p.id, p.code, p.discount_type, p.discount_value,
                     p.current_uses, p.active, p.expiry_date
            ORDER BY p.current_uses DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopUsedPromotions(@Param("limit") int limit);
}