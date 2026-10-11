package com.team22.eventticketing.sales.strategy;

import com.team22.eventticketing.sales.entity.TicketSale;
import java.time.LocalDateTime;

public class FullWindowRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        return new RefundResult(
                sale.getAmount(),
                "FullWindowRefundStrategy",
                "full_refund_granted"
        );
    }
}