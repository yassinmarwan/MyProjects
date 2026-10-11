package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.entity.SalePromotion;
import com.team22.eventticketing.sales.observer.EntityObserver;
import com.team22.eventticketing.sales.repository.SalePromotionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SalePromotionService {

    private final SalePromotionRepository salePromotionRepository;
    private final CacheEvictionService cacheEviction;
    private final List<EntityObserver> observers = new ArrayList<>();

    public SalePromotionService(SalePromotionRepository salePromotionRepository,
                                CacheEvictionService cacheEviction) {
        this.salePromotionRepository = salePromotionRepository;
        this.cacheEviction = cacheEviction;
    }

    public void register(EntityObserver observer) { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    private void notifyObservers(String action, Object payload) {
        for (EntityObserver o : observers) o.onEvent(action, payload);
    }

    public SalePromotion create(SalePromotion salePromotion) {
        SalePromotion saved = salePromotionRepository.save(salePromotion);
        notifyObservers("SALE_PROMOTION_LINKED", Map.of("salePromotionId", saved.getId()));
        return saved;
    }

    // ─── [CRUD] Read by ID ────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "sales-service::sale-promotion", key = "#id")
    public SalePromotion findById(Long id) {
        return salePromotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "SalePromotion not found with id: " + id));
    }

    // ─── Read all (NOT cached) ────────────────────────────────────────────────────

    public List<SalePromotion> findAll() {
        return salePromotionRepository.findAll();
    }

    // ─── [CRUD] Update ────────────────────────────────────────────────────────────

    public SalePromotion update(Long id, SalePromotion patch) {
        SalePromotion existing = findById(id);

        if (patch.getDiscountApplied() != null) existing.setDiscountApplied(patch.getDiscountApplied());
        if (patch.getAppliedAt() != null)       existing.setAppliedAt(patch.getAppliedAt());
        if (patch.getTicketSale() != null)      existing.setTicketSale(patch.getTicketSale());
        if (patch.getPromotion() != null)       existing.setPromotion(patch.getPromotion());

        SalePromotion saved = salePromotionRepository.save(existing);
        cacheEviction.evictEntityAndFeatures("sale-promotion", id, "S5-F8", "S5-F9", "S5-F10");
        notifyObservers("SALE_PROMOTION_UPDATED", Map.of("salePromotionId", saved.getId()));
        return saved;
    }

    // ─── [CRUD] Delete ────────────────────────────────────────────────────────────

    public void delete(Long id) {
        if (!salePromotionRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "SalePromotion not found with id: " + id);
        }
        salePromotionRepository.deleteById(id);
        cacheEviction.evictEntityAndFeatures("sale-promotion", id, "S5-F8", "S5-F9", "S5-F10");
        notifyObservers("SALE_PROMOTION_DELETED", Map.of("salePromotionId", id));
    }
}