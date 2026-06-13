package com.exchange.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    // ── equals/hashCode contract ──────────────────────────────────────────────

    @Test
    void sameId_isEqual() {
        Order o1 = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        Order o2 = new LimitOrder("ORD-1", Side.SELL, "TSLA", 5, new BigDecimal("200.00"));
        assertEquals(o1, o2);
    }

    @Test
    void differentId_isNotEqual() {
        Order o1 = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        Order o2 = new LimitOrder("ORD-2", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        assertNotEquals(o1, o2);
    }

    @Test
    void sameId_sameHashCode() {
        Order o1 = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        Order o2 = new LimitOrder("ORD-1", Side.SELL, "TSLA", 5, new BigDecimal("200.00"));
        assertEquals(o1.hashCode(), o2.hashCode());
    }

    @Test
    void worksAsHashMapKey() {
        Order o1 = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        Order o2 = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));

        Map<Order, String> map = new HashMap<Order, String>();
        map.put(o1, "first");

        assertTrue(map.containsKey(o2), "o2 with same id should be found as map key");
        assertEquals("first", map.get(o2));
    }

    @Test
    void notEqualToNull() {
        Order o = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        assertNotEquals(null, o);
    }

    @Test
    void equalToSelf() {
        Order o = new LimitOrder("ORD-1", Side.BUY, "AAPL", 10, new BigDecimal("100.00"));
        assertEquals(o, o);
    }

    // ── LimitOrder.isMatchable ────────────────────────────────────────────────

    @Test
    void buyLimit_matchable_whenAskBelowLimit() {
        LimitOrder buy = new LimitOrder("B1", Side.BUY, "AAPL", 10, new BigDecimal("190.00"));
        assertTrue(buy.isMatchable(new BigDecimal("189.50")));
    }

    @Test
    void buyLimit_matchable_atExactLimit() {
        LimitOrder buy = new LimitOrder("B1", Side.BUY, "AAPL", 10, new BigDecimal("190.00"));
        assertTrue(buy.isMatchable(new BigDecimal("190.00")));
    }

    @Test
    void buyLimit_notMatchable_whenAskAboveLimit() {
        LimitOrder buy = new LimitOrder("B1", Side.BUY, "AAPL", 10, new BigDecimal("190.00"));
        assertFalse(buy.isMatchable(new BigDecimal("191.00")));
    }

    @Test
    void sellLimit_matchable_whenBidAboveLimit() {
        LimitOrder sell = new LimitOrder("S1", Side.SELL, "AAPL", 10, new BigDecimal("185.00"));
        assertTrue(sell.isMatchable(new BigDecimal("186.00")));
    }

    @Test
    void sellLimit_notMatchable_whenBidBelowLimit() {
        LimitOrder sell = new LimitOrder("S1", Side.SELL, "AAPL", 10, new BigDecimal("185.00"));
        assertFalse(sell.isMatchable(new BigDecimal("184.00")));
    }

    // ── MarketOrder.isMatchable ───────────────────────────────────────────────

    @Test
    void marketOrder_alwaysMatchable_whenPriceExists() {
        MarketOrder mo = new MarketOrder("MO-1", Side.BUY, "AAPL", 5);
        assertTrue(mo.isMatchable(new BigDecimal("1.00")));
    }

    @Test
    void marketOrder_notMatchable_whenNullPrice() {
        MarketOrder mo = new MarketOrder("MO-1", Side.BUY, "AAPL", 5);
        assertFalse(mo.isMatchable(null));
    }

    // ── Account ───────────────────────────────────────────────────────────────

    @Test
    void account_reserveCash_reducesAvailable() {
        Account acc = new Account("A1", "Alice", new BigDecimal("10000.00"));
        acc.reserveCash(new BigDecimal("3000.00"));
        assertEquals(new BigDecimal("7000.00"), acc.getAvailableCash());
    }

    @Test
    void account_reserveCash_throwsOnOverdraft() {
        Account acc = new Account("A1", "Alice", new BigDecimal("100.00"));
        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                acc.reserveCash(new BigDecimal("200.00"));
            }
        });
    }

    @Test
    void account_adjustPosition_throwsOnShortSell() {
        Account acc = new Account("A1", "Alice", new BigDecimal("10000.00"));
        assertThrows(IllegalStateException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                acc.adjustPosition("AAPL", -1);
            }
        });
    }

    @Test
    void account_positions_returnsDefensiveCopy() {
        Account acc = new Account("A1", "Alice", new BigDecimal("10000.00"));
        acc.adjustPosition("AAPL", 50);
        Map<String, Integer> copy = acc.getPositions();
        // modifying the copy should not affect the original
        copy.put("TSLA", 10);
        assertEquals(0, acc.getPositionFor("TSLA"));
    }

    // ── Instrument ────────────────────────────────────────────────────────────

    @Test
    void instrument_equalityByValue() {
        Instrument i1 = new Instrument("AAPL", "Apple Inc.", new BigDecimal("0.01"));
        Instrument i2 = new Instrument("AAPL", "Apple Inc.", new BigDecimal("0.01"));
        assertEquals(i1, i2);
    }

    // ── Trade ─────────────────────────────────────────────────────────────────

    @Test
    void trade_notionalValue() {
        Trade t = new Trade("B1", "S1", "AAPL", new BigDecimal("190.00"), 10, java.time.Instant.now());
        assertEquals(new BigDecimal("1900.00"), t.getNotionalValue());
    }
}
