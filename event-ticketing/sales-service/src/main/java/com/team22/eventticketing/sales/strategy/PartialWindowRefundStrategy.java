package com.team22.eventticketing.sales.strategy;

import com.team22.eventticketing.sales.entity.TicketSale;
import java.time.LocalDateTime;

public class PartialWindowRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        return new RefundResult(
                sale.getAmount() * 0.5,
                "PartialWindowRefundStrategy",
                "partial_refund_granted"
        );
    }
}