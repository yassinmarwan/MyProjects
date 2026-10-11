package com.team22.eventticketing.sales.strategy;

import com.team22.eventticketing.sales.entity.TicketSale;
import java.time.LocalDateTime;

public interface RefundStrategy {
    RefundResult calculateRefund(TicketSale sale, RefundRequest request, LocalDateTime eventDate);
}