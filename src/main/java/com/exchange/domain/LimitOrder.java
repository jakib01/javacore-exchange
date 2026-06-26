package com.exchange.domain;

import java.math.BigDecimal;

public class LimitOrder extends Order {

    private BigDecimal limitPrice;

    public LimitOrder(String id, Side side, String symbol, int quantity, BigDecimal limitPrice) {
        this(id, side, symbol, quantity, limitPrice, null);
    }

    public LimitOrder(String id, Side side, String symbol, int quantity, BigDecimal limitPrice, String accountId) {
        super(id, side, symbol, quantity, accountId);
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("limitPrice must be positive");
        }
        this.limitPrice = limitPrice;
    }

    @Override
    public boolean isMatchable(BigDecimal bestPrice) {
        if (bestPrice == null) {
            return false;
        }
        if (getSide() == Side.BUY) {
            // buy: we want to pay at most our limit price
            return bestPrice.compareTo(limitPrice) <= 0;
        } else {
            // sell: we want to receive at least our limit price
            return bestPrice.compareTo(limitPrice) >= 0;
        }
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    @Override
    public String toString() {
        return "LimitOrder{id=" + getId() + ", side=" + getSide() + ", symbol=" + getSymbol()
                + ", quantity=" + getQuantity() + ", limitPrice=" + limitPrice
                + ", status=" + getStatus() + "}";
    }
}
