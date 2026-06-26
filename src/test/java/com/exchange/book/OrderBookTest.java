package com.exchange.book;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.OrderStatus;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static LimitOrder limit(String id, Side side, int qty, String price) {
        return new LimitOrder(id, side, "AAPL", qty, new BigDecimal(price));
    }

    // ── The Stage 2 checkpoint ────────────────────────────────────────────────
    //
    //   SELL 100@50, SELL 50@49, then BUY 120 market
    //   => trades 50@49 + 70@50, remaining ask 30@50.

    @Test
    void checkpoint_marketBuyWalksAsksPriceThenTime() {
        OrderBook book = new OrderBook("AAPL");

        book.match(limit("S1", Side.SELL, 100, "50"));
        book.match(limit("S2", Side.SELL, 50, "49"));

        List<Trade> trades = book.match(new MarketOrder("B1", Side.BUY, "AAPL", 120));

        assertEquals(2, trades.size(), "expected exactly two fills");

        // First fill takes out the better (lower) ask price level entirely.
        Trade first = trades.get(0);
        assertEquals(0, new BigDecimal("49").compareTo(first.getPrice()), "first fill price");
        assertEquals(50, first.getQuantity(), "first fill qty");

        // Second fill takes the remaining 70 from the 50-level.
        Trade second = trades.get(1);
        assertEquals(0, new BigDecimal("50").compareTo(second.getPrice()), "second fill price");
        assertEquals(70, second.getQuantity(), "second fill qty");

        // 49 level fully consumed; 50 level has 30 left.
        assertEquals(0, book.quantityAt(Side.SELL, new BigDecimal("49")));
        assertEquals(30, book.quantityAt(Side.SELL, new BigDecimal("50")));
        assertEquals(new BigDecimal("50"), book.bestAsk().orElseThrow());
        assertTrue(book.bestBid().isEmpty(), "no bids should rest");
    }

    // ── Resting & best-of-book ────────────────────────────────────────────────

    @Test
    void unmatchedLimitOrderRests() {
        OrderBook book = new OrderBook("AAPL");
        List<Trade> trades = book.match(limit("B1", Side.BUY, 10, "100"));
        assertTrue(trades.isEmpty());
        assertEquals(new BigDecimal("100"), book.bestBid().orElseThrow());
        assertEquals(10, book.quantityAt(Side.BUY, new BigDecimal("100")));
    }

    @Test
    void bestBidIsHighest_bestAskIsLowest() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("B1", Side.BUY, 5, "98"));
        book.match(limit("B2", Side.BUY, 5, "101"));   // better bid
        book.match(limit("S1", Side.SELL, 5, "105"));
        book.match(limit("S2", Side.SELL, 5, "103"));  // better ask

        assertEquals(new BigDecimal("101"), book.bestBid().orElseThrow());
        assertEquals(new BigDecimal("103"), book.bestAsk().orElseThrow());
    }

    @Test
    void emptyBook_hasNoBestPrices() {
        OrderBook book = new OrderBook("AAPL");
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
        assertTrue(book.isEmpty());
    }

    // ── Crossing rules ────────────────────────────────────────────────────────

    @Test
    void limitBuyCrossesRestingAskAtPassivePrice() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("S1", Side.SELL, 10, "100"));

        // Aggressive buy willing to pay 105 trades at the resting 100 (price improvement).
        List<Trade> trades = book.match(limit("B1", Side.BUY, 10, "105"));

        assertEquals(1, trades.size());
        assertEquals(0, new BigDecimal("100").compareTo(trades.get(0).getPrice()));
        assertTrue(book.isEmpty(), "both orders fully filled");
    }

    @Test
    void limitBuyBelowBestAsk_doesNotCross_andRests() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("S1", Side.SELL, 10, "100"));

        List<Trade> trades = book.match(limit("B1", Side.BUY, 10, "99"));

        assertTrue(trades.isEmpty(), "99 cannot lift a 100 offer");
        assertEquals(new BigDecimal("99"), book.bestBid().orElseThrow());
        assertEquals(new BigDecimal("100"), book.bestAsk().orElseThrow());
    }

    // ── Time priority within a price level ────────────────────────────────────

    @Test
    void fifoWithinPriceLevel() {
        OrderBook book = new OrderBook("AAPL");
        LimitOrder firstIn = limit("S1", Side.SELL, 10, "100");
        LimitOrder secondIn = limit("S2", Side.SELL, 10, "100");
        book.match(firstIn);
        book.match(secondIn);

        // Buy only 10 — must hit the order that arrived first.
        List<Trade> trades = book.match(new MarketOrder("B1", Side.BUY, "AAPL", 10));

        assertEquals(1, trades.size());
        assertEquals("S1", trades.get(0).getSellOrderId(), "older order trades first");
        assertEquals(OrderStatus.FILLED, firstIn.getStatus());
        assertEquals(OrderStatus.NEW, secondIn.getStatus());
        assertEquals(10, book.quantityAt(Side.SELL, new BigDecimal("100")));
    }

    // ── Partial fills & status transitions ────────────────────────────────────

    @Test
    void partialFillUpdatesStatusAndRemainder() {
        OrderBook book = new OrderBook("AAPL");
        LimitOrder resting = limit("S1", Side.SELL, 100, "50");
        book.match(resting);

        Order incoming = new MarketOrder("B1", Side.BUY, "AAPL", 40);
        book.match(incoming);

        assertEquals(OrderStatus.PARTIALLY_FILLED, resting.getStatus());
        assertEquals(60, resting.getRemainingQuantity());
        assertEquals(OrderStatus.FILLED, incoming.getStatus());
        assertEquals(0, incoming.getRemainingQuantity());
    }

    @Test
    void marketRemainderDoesNotRest() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("S1", Side.SELL, 30, "50"));

        Order bigBuy = new MarketOrder("B1", Side.BUY, "AAPL", 100);
        List<Trade> trades = book.match(bigBuy);

        assertEquals(1, trades.size());
        assertEquals(70, bigBuy.getRemainingQuantity());
        assertEquals(OrderStatus.PARTIALLY_FILLED, bigBuy.getStatus());
        // A market order never rests on the book — there is no price to rest it at.
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.isEmpty());
    }

    @Test
    void sellCrossesMultipleBidLevels() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("B1", Side.BUY, 10, "101"));
        book.match(limit("B2", Side.BUY, 10, "100"));

        // Aggressive sell of 15 @99 lifts best bid (101) first, then 100.
        List<Trade> trades = book.match(limit("S1", Side.SELL, 15, "99"));

        assertEquals(2, trades.size());
        assertEquals(0, new BigDecimal("101").compareTo(trades.get(0).getPrice()));
        assertEquals(10, trades.get(0).getQuantity());
        assertEquals(0, new BigDecimal("100").compareTo(trades.get(1).getPrice()));
        assertEquals(5, trades.get(1).getQuantity());
        assertEquals(5, book.quantityAt(Side.BUY, new BigDecimal("100")));
    }

    // ── Account attribution on generated trades ───────────────────────────────

    @Test
    void tradeRecordsBuyAndSellAccounts() {
        OrderBook book = new OrderBook("AAPL");
        book.match(new LimitOrder("S1", Side.SELL, "AAPL", 10, new BigDecimal("50"), "ACC-SELLER"));
        List<Trade> trades = book.match(
                new LimitOrder("B1", Side.BUY, "AAPL", 10, new BigDecimal("50"), "ACC-BUYER"));

        assertEquals(1, trades.size());
        assertEquals("ACC-BUYER", trades.get(0).getBuyAccountId());
        assertEquals("ACC-SELLER", trades.get(0).getSellAccountId());
    }

    // ── Misc guards ───────────────────────────────────────────────────────────

    @Test
    void rejectsOrderForWrongSymbol() {
        OrderBook book = new OrderBook("AAPL");
        assertThrows(IllegalArgumentException.class,
                () -> book.match(new LimitOrder("X", Side.BUY, "TSLA", 1, new BigDecimal("1"))));
    }

    @Test
    void removeByIdPullsRestingOrder() {
        OrderBook book = new OrderBook("AAPL");
        book.match(limit("B1", Side.BUY, 10, "100"));
        assertTrue(book.remove("B1"));
        assertTrue(book.bestBid().isEmpty());
        assertFalse(book.remove("B1"), "second removal finds nothing");
    }
}
