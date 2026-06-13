package com.exchange.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Account {

    private String id;
    private String owner;
    private BigDecimal cashBalance;
    private BigDecimal reservedCash;
    private Map<String, Integer> positions;

    public Account(String id, String owner, BigDecimal initialCash) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id required");
        }
        if (owner == null || owner.isEmpty()) {
            throw new IllegalArgumentException("owner required");
        }
        if (initialCash == null || initialCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("initialCash must be non-negative");
        }

        this.id = id;
        this.owner = owner;
        this.cashBalance = initialCash;
        this.reservedCash = BigDecimal.ZERO;
        this.positions = new HashMap<String, Integer>();
    }

    public void reserveCash(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        BigDecimal available = cashBalance.subtract(reservedCash);
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient cash: available=" + available + ", requested=" + amount);
        }
        reservedCash = reservedCash.add(amount);
    }

    public void releaseReservedCash(BigDecimal amount) {
        if (amount.compareTo(reservedCash) > 0) {
            throw new IllegalStateException("Cannot release more than reserved");
        }
        reservedCash = reservedCash.subtract(amount);
    }

    public void debitReserved(BigDecimal amount) {
        if (amount.compareTo(reservedCash) > 0) {
            throw new IllegalStateException("Cannot debit more than reserved");
        }
        reservedCash = reservedCash.subtract(amount);
        cashBalance = cashBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        cashBalance = cashBalance.add(amount);
    }

    public void adjustPosition(String symbol, int delta) {
        int current = 0;
        if (positions.containsKey(symbol)) {
            current = positions.get(symbol);
        }
        int updated = current + delta;
        if (updated < 0) {
            throw new IllegalStateException("Short selling not supported: symbol=" + symbol
                    + ", current=" + current + ", delta=" + delta);
        }
        if (updated == 0) {
            positions.remove(symbol);
        } else {
            positions.put(symbol, updated);
        }
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public BigDecimal getReservedCash() {
        return reservedCash;
    }

    public BigDecimal getAvailableCash() {
        return cashBalance.subtract(reservedCash);
    }

    public Map<String, Integer> getPositions() {
        return new HashMap<String, Integer>(positions);
    }

    public int getPositionFor(String symbol) {
        if (positions.containsKey(symbol)) {
            return positions.get(symbol);
        }
        return 0;
    }

    @Override
    public String toString() {
        return "Account{id=" + id + ", owner=" + owner + ", cashBalance=" + cashBalance
                + ", reservedCash=" + reservedCash + ", positions=" + positions + "}";
    }
}
