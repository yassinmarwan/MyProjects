package com.team22.eventticketing.sales.controller;

import com.team22.eventticketing.sales.dto.PromotionUsageDTO;
import com.team22.eventticketing.sales.dto.RevenueReportDTO;
import com.team22.eventticketing.sales.dto.SaleDetailsDTO;
import com.team22.eventticketing.sales.dto.TierRevenueDTO;
import com.team22.eventticketing.sales.dto.UserSaleSummaryDTO;
import com.team22.eventticketing.sales.entity.TicketSale;
import static com.team22.eventticketing.sales.entity.TicketSale.PaymentMethod;

import com.team22.eventticketing.sales.service.PromotionService;
import com.team22.eventticketing.sales.service.RefundWindowPolicyService;
import com.team22.eventticketing.sales.service.SalesService;
import com.team22.eventticketing.sales.service.TicketSaleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class salesController {

    private final SalesService salesService;
    private final TicketSaleService ticketSaleService;
    private final PromotionService promotionService;
    private final RefundWindowPolicyService refundWindowPolicyService;

    public salesController(SalesService salesService,
                           TicketSaleService ticketSaleService,
                           PromotionService promotionService,
                           RefundWindowPolicyService refundWindowPolicyService) {
        this.salesService = salesService;
        this.ticketSaleService = ticketSaleService;
        this.promotionService = promotionService;
        this.refundWindowPolicyService = refundWindowPolicyService;
    }

    private void assertOwnerOrAdmin(Long saleUserId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        Long callerId = (Long) auth.getDetails();
        if (!callerId.equals(saleUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    // ─── Health Check ─────────────────────────────
    @GetMapping("/health")
    public String statusCheck() {
        return "OK";
    }

    // ─── S5-F4: Process Ticket Sale for Booking ───
    @PostMapping("/booking/{bookingId}")
    public ResponseEntity<TicketSale> payBooking(
            @PathVariable Long bookingId,
            @RequestBody Map<String, String> body,
            @RequestParam(defaultValue = "false") boolean simulateFailure
    ) {
        PaymentMethod method = PaymentMethod.valueOf(body.get("method").toUpperCase());
        String cardLastFour = body.get("cardLastFour");
        TicketSale sale = salesService.processBookingPayment(bookingId, method, cardLastFour, simulateFailure);
        return new ResponseEntity<>(sale, HttpStatus.CREATED);
    }

    // ─── S5-F5: Apply Promotion ───────────────────
    @PostMapping("/{saleId}/promotions/{promotionId}")
    public ResponseEntity<TicketSale> applyPromotion(
            @PathVariable Long saleId,
            @PathVariable Long promotionId
    ) {
        assertOwnerOrAdmin(ticketSaleService.findById(saleId).getUserId());
        TicketSale updatedSale = salesService.applyPromotionToSale(saleId, promotionId);
        return new ResponseEntity<>(updatedSale, HttpStatus.OK);
    }

    // ─── S5-F6: Revenue Report ────────────────────
    @GetMapping("/reports/revenue")
    public RevenueReportDTO getRevenueReport(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        LocalDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
        return salesService.getRevenueReport(startDateTime, endDateTime);
    }

    // ─── CRUD ─────────────────────────────────────
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketSale create(@RequestBody TicketSale ticketSale) {
        return ticketSaleService.create(ticketSale);
    }

    @GetMapping("/{id}")
    public TicketSale getById(@PathVariable Long id) {
        TicketSale sale = ticketSaleService.findById(id);
        assertOwnerOrAdmin(sale.getUserId());
        return sale;
    }

    @GetMapping
    public List<TicketSale> getAll() {
        return ticketSaleService.findAll();
    }

    @PutMapping("/{id}")
    public TicketSale update(@PathVariable Long id, @RequestBody TicketSale ticketSale) {
        assertOwnerOrAdmin(ticketSaleService.findById(id).getUserId());
        return ticketSaleService.update(id, ticketSale);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assertOwnerOrAdmin(ticketSaleService.findById(id).getUserId());
        ticketSaleService.delete(id);
    }

    // ─── S5-F2: Refund ────────────────────────────
    @PutMapping("/{id}/refund")
    public TicketSale refund(@PathVariable Long id, @RequestBody Map<String, String> body) {
        assertOwnerOrAdmin(ticketSaleService.findById(id).getUserId());
        return ticketSaleService.refund(id, body.get("reason"));
    }

    // ─── S5-F3: User Ticket Sale Summary ──────────
    @GetMapping("/user/{userId}/summary")
    public UserSaleSummaryDTO getUserSummary(@PathVariable Long userId) {
        assertOwnerOrAdmin(userId);
        return ticketSaleService.getUserSummary(userId);
    }

    // ─── S5-F8: Sale Details with Applied Promotions ──────────
    @GetMapping("/{saleId}/details")
    public SaleDetailsDTO getSaleDetails(@PathVariable Long saleId) {
        assertOwnerOrAdmin(ticketSaleService.findById(saleId).getUserId());
        return ticketSaleService.getSaleDetails(saleId);
    }

    // ─── S5-F9: Top Used Promotions Report ────────
    @GetMapping("/promotions/top-used")
    public List<PromotionUsageDTO> getTopUsedPromotions(@RequestParam(defaultValue = "10") int limit) {
        return promotionService.getTopUsedPromotions(limit);
    }

    // ─── S5-F7: Retry Failed Ticket Sale ──────────
    @PutMapping("/{id}/retry")
    public TicketSale retry(@PathVariable Long id) {
        assertOwnerOrAdmin(ticketSaleService.findById(id).getUserId());
        return ticketSaleService.retry(id);
    }

    // ─── S5-F1: Search ────────────────────────────
    @GetMapping("/search")
    public List<TicketSale> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return ticketSaleService.search(status, startDate, endDate);
    }

    // ─── S5-F10: Get Ticket Sales by Tier ─────────
    @GetMapping("/analytics/tier")
    public ResponseEntity<List<TierRevenueDTO>> getTierRevenue(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }

        ticketSaleService.logAnalyticsViewed(start, end);
        List<TierRevenueDTO> result = ticketSaleService.getTierRevenue(start, end);
        return ResponseEntity.ok(result);
    }

    // ─── S5-F12: Refund with Window Policy ────────
    @PostMapping("/{id}/refund-window-policy")
    public ResponseEntity<TicketSale> refundWithWindowPolicy(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        assertOwnerOrAdmin(ticketSaleService.findById(id).getUserId());
        TicketSale result = refundWindowPolicyService.processRefundWithWindowPolicy(
                id, body.get("reason"));
        return ResponseEntity.ok(result);
    }

    // ─── NEW S5-READ-DB: GET /api/sales/user/{userId}/total ───────────────────────
    @GetMapping("/user/{userId}/total")
    public ResponseEntity<java.math.BigDecimal> getUserTotal(
            @PathVariable Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        return ResponseEntity.ok(ticketSaleService.getUserTotal(userId, start, end));
    }
}