package com.exchange.domain;

import java.math.BigDecimal;
import java.time.Instant;

public abstract class Order {

    private String id;
    private Side side;
    private String symbol;
    private int quantity;
    private int filledQuantity;
    private String accountId;
    private Instant timestamp;
    private OrderStatus status;

    public Order(String id, Side side, String symbol, int quantity) {
        // Backwards-compatible constructor (Stage 1): no account attribution.
        this(id, side, symbol, quantity, null);
    }

    public Order(String id, Side side, String symbol, int quantity, String accountId) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id required");
        }
        if (side == null) {
            throw new IllegalArgumentException("side required");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        this.id = id;
        this.side = side;
        this.symbol = symbol;
        this.quantity = quantity;
        this.filledQuantity = 0;
        this.accountId = accountId;
        this.timestamp = Instant.now();
        this.status = OrderStatus.NEW;
    }

    public abstract boolean isMatchable(BigDecimal bestPrice);

    public String getId() {
        return id;
    }

    public Side getSide() {
        return side;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getFilledQuantity() {
        return filledQuantity;
    }

    public int getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public boolean isFullyFilled() {
        return filledQuantity == quantity;
    }

    public String getAccountId() {
        return accountId;
    }

    /**
     * Apply a fill of {@code qty} units against this order. Advances the status
     * NEW -> PARTIALLY_FILLED -> FILLED automatically as quantity is consumed.
     */
    public void fill(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("fill quantity must be positive");
        }
        if (qty > getRemainingQuantity()) {
            throw new IllegalStateException("cannot fill " + qty
                    + " units, only " + getRemainingQuantity() + " remaining on order " + id);
        }
        filledQuantity += qty;
        status = isFullyFilled() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        this.status = status;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Order)) return false;
        Order other = (Order) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", side=" + side + ", symbol=" + symbol
                + ", quantity=" + quantity + ", status=" + status + "}";
    }
}
