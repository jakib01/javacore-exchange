package com.exchange.domain;

import java.math.BigDecimal;

public class Instrument {

    private String symbol;
    private String name;
    private BigDecimal tickSize;

    public Instrument(String symbol, String name, BigDecimal tickSize) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol required");
        }
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tickSize must be positive");
        }
        this.symbol = symbol;
        this.name = name;
        this.tickSize = tickSize;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof Instrument)) return false;
        Instrument other = (Instrument) obj;
        return symbol.equals(other.symbol)
                && name.equals(other.name)
                && tickSize.equals(other.tickSize);
    }

    @Override
    public int hashCode() {
        int result = symbol.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + tickSize.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Instrument{symbol=" + symbol + ", name=" + name + ", tickSize=" + tickSize + "}";
    }
}
