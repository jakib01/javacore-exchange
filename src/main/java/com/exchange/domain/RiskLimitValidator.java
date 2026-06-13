package com.exchange.domain;

import java.math.BigDecimal;

public class RiskLimitValidator implements OrderValidator {

    private BigDecimal maxOrderNotional;

    public RiskLimitValidator(BigDecimal maxOrderNotional) {
        if (maxOrderNotional == null || maxOrderNotional.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxOrderNotional must be positive");
        }
        this.maxOrderNotional = maxOrderNotional;
    }

    @Override
    public String validate(Order order, Account account) {
        if (order instanceof LimitOrder) {
            LimitOrder limitOrder = (LimitOrder) order;
            BigDecimal notional = limitOrder.getLimitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            if (notional.compareTo(maxOrderNotional) > 0) {
                return "order notional " + notional + " exceeds risk limit " + maxOrderNotional;
            }
        }
        return null;
    }
}
