package com.team22.eventticketing.sales.strategy;

public class RefundResult {
    private final double refundAmount;
    private final String strategyName;
    private final String reasonCode;

    public RefundResult(double refundAmount, String strategyName, String reasonCode) {
        this.refundAmount = refundAmount;
        this.strategyName = strategyName;
        this.reasonCode = reasonCode;
    }

    public double getRefundAmount() { return refundAmount; }
    public String getStrategyName() { return strategyName; }
    public String getReasonCode() { return reasonCode; }
}