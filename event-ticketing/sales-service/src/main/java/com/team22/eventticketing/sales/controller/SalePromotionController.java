package com.team22.eventticketing.sales.controller;

import com.team22.eventticketing.sales.entity.SalePromotion;
import com.team22.eventticketing.sales.service.SalePromotionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sale-promotions")
public class SalePromotionController {

    private final SalePromotionService salePromotionService;

    public SalePromotionController(SalePromotionService salePromotionService) {
        this.salePromotionService = salePromotionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalePromotion create(@RequestBody SalePromotion salePromotion) {
        return salePromotionService.create(salePromotion);
    }

    @GetMapping("/{id}")
    public SalePromotion getById(@PathVariable Long id) {
        return salePromotionService.findById(id);
    }

    @GetMapping
    public List<SalePromotion> getAll() {
        return salePromotionService.findAll();
    }

    @PutMapping("/{id}")
    public SalePromotion update(@PathVariable Long id, @RequestBody SalePromotion salePromotion) {
        return salePromotionService.update(id, salePromotion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        salePromotionService.delete(id);
    }
}
