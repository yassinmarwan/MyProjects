package com.team22.eventticketing.sales.service;

import com.team22.eventticketing.sales.entity.Promotion;
import com.team22.eventticketing.sales.entity.TicketSale;
import com.team22.eventticketing.sales.messaging.publishers.PaymentEventPublisher;
import com.team22.eventticketing.sales.repository.PromotionRepository;
import com.team22.eventticketing.sales.repository.SalePromotionRepository;
import com.team22.eventticketing.sales.repository.TicketSaleRepository;
import com.team22.eventticketing.contracts.dto.BookingDTO;
import com.team22.eventticketing.contracts.feign.BookingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesServiceUnitTest {

    @Mock TicketSaleRepository ticketSaleRepository;
    @Mock PromotionRepository promotionRepository;
    @Mock SalePromotionRepository salePromotionRepository;
    @Mock CacheEvictionService cacheEviction;
    @Mock BookingServiceClient bookingServiceClient;
    @Mock PaymentEventPublisher paymentEventPublisher;

    SalesService salesService;

    @BeforeEach
    void setUp() {
        salesService = new SalesService(
                ticketSaleRepository, promotionRepository, salePromotionRepository,
                cacheEviction, bookingServiceClient, paymentEventPublisher);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TicketSale pendingSale(Long bookingId, double amount) {
        TicketSale sale = new TicketSale(bookingId, 1L, amount,
                TicketSale.PaymentMethod.CREDIT_CARD, TicketSale.SaleStatus.PENDING);
        sale.setId(10L + bookingId);
        return sale;
    }

    private BookingDTO bookingInStatus(Long bookingId, String status) {
        return new BookingDTO(bookingId, 1L, 5L, status, BigDecimal.valueOf(500.0));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // [S5-F4] processBookingPayment — Feign-backed payment processing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Happy path: PAYMENT_PENDING booking + PENDING sale → COMPLETED + payment.completed published")
    void processPayment_happyPath_completesTicketSaleAndPublishesCompleted() {
        when(bookingServiceClient.getBooking(1L)).thenReturn(bookingInStatus(1L, "PAYMENT_PENDING"));
        TicketSale sale = pendingSale(1L, 500.0);
        when(ticketSaleRepository.findByBookingIdAndStatusPending(1L)).thenReturn(Optional.of(sale));
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketSale result = salesService.processBookingPayment(
                1L, TicketSale.PaymentMethod.CREDIT_CARD, "4242");

        assertThat(result.getStatus()).isEqualTo(TicketSale.SaleStatus.COMPLETED);
        assertThat(result.getMethod()).isEqualTo(TicketSale.PaymentMethod.CREDIT_CARD);
        assertThat(result.getTransactionDetails()).containsEntry("cardLastFour", "4242");
        assertThat(result.getTransactionDetails()).containsEntry("gatewayResponse", "approved");
        verify(paymentEventPublisher).publishPaymentCompleted(anyLong(), eq(1L), eq(500.0));
        verify(paymentEventPublisher, never()).publishPaymentFailed(any(), any(), any());
    }

    @Test
    @DisplayName("simulateFailure=true → FAILED status + payment.failed published, not payment.completed")
    void processPayment_simulateFailure_setsFailedAndPublishesFailed() {
        when(bookingServiceClient.getBooking(2L)).thenReturn(bookingInStatus(2L, "PAYMENT_PENDING"));
        TicketSale sale = pendingSale(2L, 300.0);
        when(ticketSaleRepository.findByBookingIdAndStatusPending(2L)).thenReturn(Optional.of(sale));
        when(ticketSaleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketSale result = salesService.processBookingPayment(
                2L, TicketSale.PaymentMethod.DEBIT_CARD, null, true);

        assertThat(result.getStatus()).isEqualTo(TicketSale.SaleStatus.FAILED);
        assertThat(result.getTransactionDetails()).containsEntry("gatewayResponse", "declined");
        verify(paymentEventPublisher).publishPaymentFailed(anyLong(), eq(2L), eq("payment declined"));
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any(), any(), anyDouble());
    }

    @Test
    @DisplayName("Booking not in PAYMENT_PENDING → 400 BAD_REQUEST; repository never touched")
    void processPayment_bookingWrongStatus_throws400AndSkipsRepo() {
        when(bookingServiceClient.getBooking(3L)).thenReturn(bookingInStatus(3L, "CONFIRMED"));

        assertThatThrownBy(() ->
                salesService.processBookingPayment(3L, TicketSale.PaymentMethod.PAYPAL, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CONFIRMED");

        verify(ticketSaleRepository, never()).findByBookingIdAndStatusPending(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("No PENDING TicketSale found → 404 NOT_FOUND; publisher never called")
    void processPayment_noPendingTicketSale_throws404() {
        when(bookingServiceClient.getBooking(4L)).thenReturn(bookingInStatus(4L, "PAYMENT_PENDING"));
        when(ticketSaleRepository.findByBookingIdAndStatusPending(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                salesService.processBookingPayment(4L, TicketSale.PaymentMethod.WALLET, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Pending ticket sale not found");

        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("Feign 404 from booking-service → 404 ResponseStatusException with booking id")
    void processPayment_bookingFeignNotFound_wrapsTo404() {
        when(bookingServiceClient.getBooking(99L))
                .thenThrow(mock(feign.FeignException.NotFound.class));

        assertThatThrownBy(() ->
                salesService.processBookingPayment(99L, TicketSale.PaymentMethod.CREDIT_CARD, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Booking not found");
    }

    @Test
    @DisplayName("Feign generic error from booking-service → 503 SERVICE_UNAVAILABLE")
    void processPayment_bookingFeignUnavailable_wrapsTo503() {
        feign.FeignException feignEx = mock(feign.FeignException.class);
        when(bookingServiceClient.getBooking(98L)).thenThrow(feignEx);

        assertThatThrownBy(() ->
                salesService.processBookingPayment(98L, TicketSale.PaymentMethod.CREDIT_CARD, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // [S5-F5] applyPromotionToSale
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Percentage promotion 20% off $100 → discountApplied == 20.0; currentUses incremented")
    void applyPromotion_percentage_calculatesCorrectDiscount() {
        TicketSale sale = pendingSale(10L, 100.0);
        Promotion promo = activePromo("PROMO20", Promotion.DiscountType.PERCENTAGE, 20.0, 100);
        promo.setId(1L);

        when(ticketSaleRepository.findById(10L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promo));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(10L, 1L)).thenReturn(false);
        when(salePromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketSale result = salesService.applyPromotionToSale(10L, 1L);

        assertThat(result.getSalePromotions()).hasSize(1);
        assertThat(result.getSalePromotions().get(0).getDiscountApplied()).isEqualTo(20.0);
        assertThat(promo.getCurrentUses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fixed $15 off $100 → discountApplied == 15.0")
    void applyPromotion_fixed_calculatesCorrectDiscount() {
        TicketSale sale = pendingSale(11L, 100.0);
        Promotion promo = activePromo("FIXED15", Promotion.DiscountType.FIXED, 15.0, 50);
        promo.setId(2L);

        when(ticketSaleRepository.findById(11L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(2L)).thenReturn(Optional.of(promo));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(11L, 2L)).thenReturn(false);
        when(salePromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketSale result = salesService.applyPromotionToSale(11L, 2L);

        assertThat(result.getSalePromotions().get(0).getDiscountApplied()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Fixed discount larger than sale amount → capped at sale amount")
    void applyPromotion_fixedExceedsSaleAmount_cappedAtAmount() {
        TicketSale sale = pendingSale(12L, 10.0); // small amount
        Promotion promo = activePromo("BIG", Promotion.DiscountType.FIXED, 999.0, 50);
        promo.setId(3L);

        when(ticketSaleRepository.findById(12L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(3L)).thenReturn(Optional.of(promo));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(12L, 3L)).thenReturn(false);
        when(salePromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TicketSale result = salesService.applyPromotionToSale(12L, 3L);

        assertThat(result.getSalePromotions().get(0).getDiscountApplied()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Expired promotion → 400 BAD_REQUEST")
    void applyPromotion_expired_throws400() {
        TicketSale sale = pendingSale(13L, 100.0);
        Promotion promo = activePromo("EXP", Promotion.DiscountType.PERCENTAGE, 10.0, 100);
        promo.setExpiryDate(LocalDateTime.now().minusDays(1));
        promo.setId(4L);

        when(ticketSaleRepository.findById(13L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(4L)).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> salesService.applyPromotionToSale(13L, 4L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Promotion at max uses → 400 BAD_REQUEST")
    void applyPromotion_maxUsesReached_throws400() {
        TicketSale sale = pendingSale(14L, 100.0);
        Promotion promo = activePromo("MAXED", Promotion.DiscountType.PERCENTAGE, 10.0, 5);
        promo.setCurrentUses(5);
        promo.setId(5L);

        when(ticketSaleRepository.findById(14L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(5L)).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> salesService.applyPromotionToSale(14L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("Promotion already applied to this sale → 400 BAD_REQUEST")
    void applyPromotion_duplicate_throws400() {
        TicketSale sale = pendingSale(15L, 100.0);
        Promotion promo = activePromo("DUP", Promotion.DiscountType.FIXED, 5.0, 100);
        promo.setId(6L);

        when(ticketSaleRepository.findById(15L)).thenReturn(Optional.of(sale));
        when(promotionRepository.findById(6L)).thenReturn(Optional.of(promo));
        when(salePromotionRepository.existsByTicketSaleIdAndPromotionId(15L, 6L)).thenReturn(true);

        assertThatThrownBy(() -> salesService.applyPromotionToSale(15L, 6L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already applied");
    }

    @Test
    @DisplayName("Applying promotion to COMPLETED sale → 400 BAD_REQUEST")
    void applyPromotion_completedSale_throws400() {
        TicketSale sale = new TicketSale(20L, 1L, 100.0,
                TicketSale.PaymentMethod.CREDIT_CARD, TicketSale.SaleStatus.COMPLETED);
        sale.setId(30L);
        Promotion promo = activePromo("LATE", Promotion.DiscountType.FIXED, 5.0, 100);
        promo.setId(7L);

        when(ticketSaleRepository.findById(30L)).thenReturn(Optional.of(sale));

        assertThatThrownBy(() -> salesService.applyPromotionToSale(30L, 7L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("completed");
    }

    // ── factory ───────────────────────────────────────────────────────────────

    private Promotion activePromo(String code, Promotion.DiscountType type,
                                  double value, int maxUses) {
        Promotion p = new Promotion(code, type, value, maxUses,
                LocalDateTime.now().plusDays(7));
        p.setActive(true);
        return p;
    }
}
