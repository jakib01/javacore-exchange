package com.exchange.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        // create an instrument
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("0.01"));
        System.out.println("Instrument: " + aapl);

        // create accounts
        Account alice = new Account("ACC-001", "Alice", new BigDecimal("50000.00"));
        Account bob = new Account("ACC-002", "Bob", new BigDecimal("30000.00"));
        bob.adjustPosition("AAPL", 100);
        System.out.println("Alice: " + alice);
        System.out.println("Bob:   " + bob);

        // create orders
        LimitOrder buyOrder = new LimitOrder("ORD-001", Side.BUY, "AAPL", 10, new BigDecimal("190.00"));
        LimitOrder sellOrder = new LimitOrder("ORD-002", Side.SELL, "AAPL", 10, new BigDecimal("189.00"));
        System.out.println("\nBuy order:  " + buyOrder);
        System.out.println("Sell order: " + sellOrder);

        // validate orders
        List<OrderValidator> validators = new ArrayList<OrderValidator>();
        validators.add(new BasicValidator());
        validators.add(new RiskLimitValidator(new BigDecimal("100000.00")));

        for (int i = 0; i < validators.size(); i++) {
            OrderValidator v = validators.get(i);
            String buyError = v.validate(buyOrder, alice);
            if (buyError != null) {
                throw new IllegalArgumentException("Buy order rejected: " + buyError);
            }
            String sellError = v.validate(sellOrder, bob);
            if (sellError != null) {
                throw new IllegalArgumentException("Sell order rejected: " + sellError);
            }
        }
        System.out.println("\nBoth orders passed validation.");

        // check if orders are matchable at a market price
        BigDecimal marketPrice = new BigDecimal("189.50");
        System.out.println("\nAt market price " + marketPrice + ":");
        System.out.println("  Buy matchable:  " + buyOrder.isMatchable(marketPrice));
        System.out.println("  Sell matchable: " + sellOrder.isMatchable(marketPrice));

        // execute trade manually
        BigDecimal tradePrice = new BigDecimal("189.50");
        int tradeQty = 10;
        BigDecimal notional = tradePrice.multiply(BigDecimal.valueOf(tradeQty));

        alice.reserveCash(notional);
        alice.debitReserved(notional);
        alice.adjustPosition("AAPL", tradeQty);

        bob.credit(notional);
        bob.adjustPosition("AAPL", -tradeQty);

        Trade trade = new Trade(buyOrder.getId(), sellOrder.getId(), "AAPL", tradePrice, tradeQty, Instant.now());
        buyOrder.setStatus(OrderStatus.FILLED);
        sellOrder.setStatus(OrderStatus.FILLED);

        System.out.println("\nTrade executed: " + trade);
        System.out.println("Trade notional: " + trade.getNotionalValue());
        System.out.println("\nAlice after: " + alice);
        System.out.println("Bob after:   " + bob);

        // check order types with instanceof
        if (buyOrder instanceof LimitOrder) {
            LimitOrder lo = (LimitOrder) buyOrder;
            System.out.println("\n" + buyOrder.getId() + " is a Limit order @ " + lo.getLimitPrice() + " — status: " + buyOrder.getStatus());
        }
        if (sellOrder instanceof LimitOrder) {
            LimitOrder lo = (LimitOrder) sellOrder;
            System.out.println(sellOrder.getId() + " is a Limit order @ " + lo.getLimitPrice() + " — status: " + sellOrder.getStatus());
        }
    }
}
