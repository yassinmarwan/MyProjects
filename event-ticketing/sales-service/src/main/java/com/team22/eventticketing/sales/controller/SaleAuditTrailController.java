package com.team22.eventticketing.sales.controller;

import com.team22.eventticketing.sales.dto.SaleAuditTrailDTO;
import com.team22.eventticketing.sales.service.SaleAuditTrailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleAuditTrailController {

    private final SaleAuditTrailService saleAuditTrailService;

    public SaleAuditTrailController(SaleAuditTrailService saleAuditTrailService) {
        this.saleAuditTrailService = saleAuditTrailService;
    }

    @GetMapping("/{id}/audit-trail")
    public ResponseEntity<SaleAuditTrailDTO> getAuditTrail(@PathVariable Long id) {
        return ResponseEntity.ok(saleAuditTrailService.getAuditTrail(id));
    }
}
