package com.exchange.domain;

import java.math.BigDecimal;
import java.time.Instant;

public class Trade {

    private String buyOrderId;
    private String sellOrderId;
    private String symbol;
    private BigDecimal price;
    private int quantity;
    private Instant executedAt;

    public Trade(String buyOrderId, String sellOrderId, String symbol,
                 BigDecimal price, int quantity, Instant executedAt) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    public BigDecimal getNotionalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    @Override
    public String toString() {
        return "Trade{buyOrderId=" + buyOrderId + ", sellOrderId=" + sellOrderId
                + ", symbol=" + symbol + ", price=" + price
                + ", quantity=" + quantity + ", executedAt=" + executedAt + "}";
    }
}
