package com.exchange.domain;

import java.math.BigDecimal;

public class MarketOrder extends Order {

    public MarketOrder(String id, Side side, String symbol, int quantity) {
        super(id, side, symbol, quantity);
    }

    public MarketOrder(String id, Side side, String symbol, int quantity, String accountId) {
        super(id, side, symbol, quantity, accountId);
    }

    @Override
    public boolean isMatchable(BigDecimal bestPrice) {
        return bestPrice != null;
    }

    @Override
    public String toString() {
        return "MarketOrder{id=" + getId() + ", side=" + getSide() + ", symbol=" + getSymbol()
                + ", quantity=" + getQuantity() + ", status=" + getStatus() + "}";
    }
}
