package com.team22.eventticketing.sales.strategy;

import com.team22.eventticketing.sales.entity.TicketSale;
import java.time.LocalDateTime;

public class NoRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate) {
        return new RefundResult(
                0.0,
                "NoRefundStrategy",
                "refund_window_expired"
        );
    }
}