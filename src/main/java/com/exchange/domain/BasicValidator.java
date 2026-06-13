package com.exchange.domain;

import java.math.BigDecimal;

public class BasicValidator implements OrderValidator {

    @Override
    public String validate(Order order, Account account) {
        if (order.getQuantity() <= 0) {
            return "quantity must be positive";
        }

        if (order.getSide() == Side.BUY && order instanceof LimitOrder) {
            LimitOrder limitOrder = (LimitOrder) order;
            BigDecimal required = limitOrder.getLimitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            if (account.getAvailableCash().compareTo(required) < 0) {
                return "insufficient cash: need " + required + ", have " + account.getAvailableCash();
            }
        }

        return null;
    }
}
