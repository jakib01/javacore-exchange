package com.exchange.engine;

import com.exchange.domain.Account;
import com.exchange.domain.Instrument;
import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;
import com.exchange.exception.InsufficientFundsException;
import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.UnknownInstrumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeTest {

    private Exchange exchange;
    private Account buyer;    // ACC1: cash, no stock
    private Account seller;   // MM:  stock, little cash

    @BeforeEach
    void setUp() {
        exchange = new Exchange();
        exchange.listInstrument(new Instrument("AAPL", "Apple Inc.", new BigDecimal("0.01")));

        buyer = new Account("ACC1", "Alice", new BigDecimal("1000000.00"));
        seller = new Account("MM", "MarketMaker", new BigDecimal("0.00"));
        seller.adjustPosition("AAPL", 1000);

        exchange.openAccount(buyer);
        exchange.openAccount(seller);
    }

    private LimitOrder limit(String id, Side side, int qty, String price, String account) {
        return new LimitOrder(id, side, "AAPL", qty, new BigDecimal(price), account);
    }

    // ── Happy path: match + settlement ───────────────────────────────────────────

    @Test
    void matchedTradeMovesCashAndPositions() {
        exchange.submit(limit("S1", Side.SELL, 100, "190.00", "MM"));   // rests
        List<Trade> trades = exchange.submit(limit("B1", Side.BUY, 100, "190.00", "ACC1"));

        assertEquals(1, trades.size());

        // Buyer paid 19,000 and now holds 100 shares.
        assertEquals(0, new BigDecimal("981000.00").compareTo(buyer.getCashBalance()));
        assertEquals(100, buyer.getPositionFor("AAPL"));

        // Seller received 19,000 and shed 100 shares.
        assertEquals(0, new BigDecimal("19000.00").compareTo(seller.getCashBalance()));
        assertEquals(900, seller.getPositionFor("AAPL"));
    }

    // ── The three domain exceptions ──────────────────────────────────────────────

    @Test
    void unknownInstrumentIsRejected() {
        UnknownInstrumentException e = assertThrows(UnknownInstrumentException.class,
                () -> exchange.submit(new LimitOrder("X", Side.BUY, "TSLA", 1, new BigDecimal("1"), "ACC1")));
        assertEquals("TSLA", e.getSymbol());
    }

    @Test
    void insufficientFundsIsRejectedWithNumbers() {
        // ACC1 has 1,000,000 but a 1,000,000-share buy @200 needs 200,000,000.
        InsufficientFundsException e = assertThrows(InsufficientFundsException.class,
                () -> exchange.submit(limit("B1", Side.BUY, 1_000_000, "200.00", "ACC1")));

        assertEquals("ACC1", e.getAccountId());
        assertEquals(0, new BigDecimal("200000000.00").compareTo(e.getRequired()));
    }

    @Test
    void shortSellIsRejected() {
        // ACC1 holds no AAPL, so it cannot sell any.
        assertThrows(InvalidOrderException.class,
                () -> exchange.submit(limit("S1", Side.SELL, 10, "190.00", "ACC1")));
    }

    @Test
    void orderForUnknownAccountIsRejected() {
        assertThrows(InvalidOrderException.class,
                () -> exchange.submit(limit("B1", Side.BUY, 10, "190.00", "GHOST")));
    }

    @Test
    void noTradeMeansNoSettlement() {
        // A resting order that does not cross must not move any money.
        exchange.submit(limit("B1", Side.BUY, 10, "180.00", "ACC1"));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(buyer.getCashBalance()));
        assertEquals(0, buyer.getPositionFor("AAPL"));
    }

    // ── P&L ──────────────────────────────────────────────────────────────────────

    @Test
    void pnlIsZeroInAFlatMarket() {
        exchange.submit(limit("S1", Side.SELL, 100, "190.00", "MM"));
        exchange.submit(limit("B1", Side.BUY, 100, "190.00", "ACC1"));

        // Bought 100 @190, mark is still 190 -> equity unchanged -> P&L 0.
        PnlReport report = exchange.pnl("ACC1");
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getPnl()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(report.getEquity()));
    }

    @Test
    void pnlRisesWhenTheMarkRises() {
        Account other = new Account("ACC2", "Bob", new BigDecimal("1000000.00"));
        exchange.openAccount(other);

        exchange.submit(limit("S1", Side.SELL, 100, "190.00", "MM"));
        exchange.submit(limit("B1", Side.BUY, 100, "190.00", "ACC1"));   // ACC1 holds 100 @190

        // A later trade prints higher, lifting the mark used to value ACC1's stock.
        exchange.submit(limit("S2", Side.SELL, 50, "200.00", "MM"));
        exchange.submit(limit("B2", Side.BUY, 50, "200.00", "ACC2"));

        PnlReport report = exchange.pnl("ACC1");
        // 100 shares * (200 - 190) = +1,000 unrealised.
        assertEquals(0, new BigDecimal("1000.00").compareTo(report.getPnl()));
    }

    @Test
    void pnlForUnknownAccountThrows() {
        assertThrows(InvalidOrderException.class, () -> exchange.pnl("NOPE"));
    }

    // ── Cancel ───────────────────────────────────────────────────────────────────

    @Test
    void cancelPullsOrderOffTheBook() {
        exchange.submit(limit("B1", Side.BUY, 10, "180.00", "ACC1"));
        assertTrue(exchange.book("AAPL").bestBid().isPresent());

        Order cancelled = exchange.cancel("B1").orElseThrow();
        assertEquals("B1", cancelled.getId());
        assertTrue(exchange.book("AAPL").bestBid().isEmpty(), "book should be empty after cancel");
    }

    @Test
    void marketBuyWithNoAsksJustDoesNothing() {
        // estimateMarketBuyCost over an empty ask side is 0, so it is affordable
        // and simply produces no trades.
        List<Trade> trades = exchange.submit(new MarketOrder("B1", Side.BUY, "AAPL", 10, "ACC1"));
        assertTrue(trades.isEmpty());
    }
}
