package com.team22.eventticketing.sales.strategy;

public class RefundStrategySelector {

    public RefundStrategy select(long hoursUntilEvent) {
        if (hoursUntilEvent > 48) {
            return new FullWindowRefundStrategy();
        } else if (hoursUntilEvent > 24) {
            return new PartialWindowRefundStrategy();
        } else {
            return new NoRefundStrategy();
        }
    }
}