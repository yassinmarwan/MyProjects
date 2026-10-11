package com.team22.eventticketing.sales.config;

import com.team22.eventticketing.sales.observer.MongoEventLogger;
import com.team22.eventticketing.sales.service.PromotionService;
import com.team22.eventticketing.sales.service.RefundWindowPolicyService;
import com.team22.eventticketing.sales.service.SalePromotionService;
import com.team22.eventticketing.sales.service.SalesService;
import com.team22.eventticketing.sales.service.TicketSaleService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserverConfig {

    private final TicketSaleService ticketSaleService;
    private final PromotionService promotionService;
    private final SalePromotionService salePromotionService;
    private final SalesService salesService;
    private final RefundWindowPolicyService refundWindowPolicyService;
    private final MongoEventLogger mongoEventLogger;

    public ObserverConfig(TicketSaleService ticketSaleService,
                          PromotionService promotionService,
                          SalePromotionService salePromotionService,
                          SalesService salesService,
                          RefundWindowPolicyService refundWindowPolicyService,
                          MongoEventLogger mongoEventLogger) {
        this.ticketSaleService = ticketSaleService;
        this.promotionService = promotionService;
        this.salePromotionService = salePromotionService;
        this.salesService = salesService;
        this.refundWindowPolicyService = refundWindowPolicyService;
        this.mongoEventLogger = mongoEventLogger;
    }

    @PostConstruct
    public void registerObservers() {
        ticketSaleService.register(mongoEventLogger);
        promotionService.register(mongoEventLogger);
        salePromotionService.register(mongoEventLogger);
        salesService.register(mongoEventLogger);
        refundWindowPolicyService.register(mongoEventLogger);
    }
}