package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.dto.PromotionUsageDTO;
import com.team22.eventticketing.sales.entity.Promotion;
import com.team22.eventticketing.sales.entity.*;
import com.team22.eventticketing.sales.observer.EntityObserver;
import com.team22.eventticketing.sales.repository.PromotionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CacheEvictionService cacheEviction;
    private final List<EntityObserver> observers = new ArrayList<>();

    public PromotionService(PromotionRepository promotionRepository,
                            CacheEvictionService cacheEviction) {
        this.promotionRepository = promotionRepository;
        this.cacheEviction = cacheEviction;
    }

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }

    // ─── Create ──────────────────────────────────────────────────────────────────

    public Promotion create(Promotion promotion) {
        Promotion saved = promotionRepository.save(promotion);
        notifyObservers("PROMOTION_CREATED", Map.of("promotionId", saved.getId()));
        return saved;
    }

    // ─── [CRUD] Read by ID ────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "sales-service::promotion", key = "#id")
    public Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Promotion not found with id: " + id));
    }

    // ─── Read all (NOT cached) ────────────────────────────────────────────────────

    public List<Promotion> findAll() {
        return promotionRepository.findAll();
    }

    // ─── [CRUD] Update ────────────────────────────────────────────────────────────

    public Promotion update(Long id, Promotion patch) {
        Promotion existing = findById(id);

        if (patch.getCode() != null)          existing.setCode(patch.getCode());
        if (patch.getDiscountType() != null)  existing.setDiscountType(patch.getDiscountType());
        if (patch.getDiscountValue() != null) existing.setDiscountValue(patch.getDiscountValue());
        if (patch.getMaxUses() != null)       existing.setMaxUses(patch.getMaxUses());
        if (patch.getCurrentUses() != null)   existing.setCurrentUses(patch.getCurrentUses());
        if (patch.getExpiryDate() != null)    existing.setExpiryDate(patch.getExpiryDate());
        if (patch.getActive() != null)        existing.setActive(patch.getActive());
        if (patch.getMetadata() != null)      existing.setMetadata(patch.getMetadata());

        Promotion saved = promotionRepository.save(existing);
        cacheEviction.evictEntityAndFeatures("promotion", id, "S5-F9");
        notifyObservers("PROMOTION_UPDATED", Map.of("promotionId", saved.getId()));
        return saved;
    }

    // ─── [S5-F9] Top Used Promotions Report (combined) ───────────────────────────

    @Cacheable(cacheNames = "sales-service::S5-F9", key = "#limit")
    public List<PromotionUsageDTO> getTopUsedPromotions(int limit) {
        List<Object[]> rows = promotionRepository.findTopUsedPromotions(limit);
        return rows.stream().map(row -> {
            Long promotionId       = ((Number) row[0]).longValue();
            String code            = (String) row[1];
            Promotion.DiscountType discType  = Promotion.DiscountType.valueOf((String) row[2]);
            Double discountValue   = ((Number) row[3]).doubleValue();
            int timesUsed          = ((Number) row[4]).intValue();
            Double totalDiscount   = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;
            Boolean active         = row[6] instanceof Boolean b ? b : Boolean.valueOf(row[6].toString());
            LocalDateTime expiry;
            Object expiryObj = row[7];
            if (expiryObj instanceof Timestamp ts) {
                expiry = ts.toLocalDateTime();
            } else if (expiryObj instanceof LocalDateTime ldt) {
                expiry = ldt;
            } else if (expiryObj instanceof OffsetDateTime odt) {
                expiry = odt.toLocalDateTime();
            } else {
                expiry = LocalDateTime.parse(expiryObj.toString().replace(" ", "T"));
            }
            boolean expired        = expiry.isBefore(LocalDateTime.now());

            return PromotionUsageDTO.builder()
                    .promotionId(promotionId)
                    .code(code)
                    .discountType(discType)
                    .discountValue(discountValue)
                    .timesUsed(timesUsed)
                    .totalDiscountGiven(totalDiscount)
                    .active(active)
                    .expired(expired)
                    .build();
        }).toList();
    }

    // ─── [CRUD] Delete ────────────────────────────────────────────────────────────

    public void delete(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Promotion not found with id: " + id);
        }
        promotionRepository.deleteById(id);
        cacheEviction.evictEntityAndFeatures("promotion", id, "S5-F9");
        notifyObservers("PROMOTION_DELETED", Map.of("promotionId", id));
    }
}
