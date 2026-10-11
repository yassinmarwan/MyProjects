package com.team22.eventticketing.sales.controller;

import com.team22.eventticketing.sales.dto.PromotionUsageDTO;
import com.team22.eventticketing.sales.entity.Promotion;
import com.team22.eventticketing.sales.service.PromotionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Promotion create(@RequestBody Promotion promotion) {
        return promotionService.create(promotion);
    }

    @GetMapping("/top-used")
    public List<PromotionUsageDTO> getTopUsedPromotions(@RequestParam(defaultValue = "10") int limit) {
        return promotionService.getTopUsedPromotions(limit);
    }

    @GetMapping("/{id}")
    public Promotion getById(@PathVariable Long id) {
        return promotionService.findById(id);
    }

    @GetMapping
    public List<Promotion> getAll() {
        return promotionService.findAll();
    }

    @PutMapping("/{id}")
    public Promotion update(@PathVariable Long id, @RequestBody Promotion promotion) {
        return promotionService.update(id, promotion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        promotionService.delete(id);
    }
}
