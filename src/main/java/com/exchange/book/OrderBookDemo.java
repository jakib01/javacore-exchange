package com.exchange.book;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * A runnable walkthrough of the Stage 2 pieces: register orders, rest them in an
 * {@link OrderBook}, run the checkpoint match, then analyse the fills with a
 * {@link TradeLog}. Run with {@code mvn -q compile exec:java} or from the IDE.
 */
public class OrderBookDemo {

    public static void main(String[] args) {
        OrderRegistry registry = new OrderRegistry();
        OrderBook book = new OrderBook("AAPL");
        TradeLog tradeLog = new TradeLog();

        // Two resting sells (best ask is the lower one: 49).
        submit(registry, book, tradeLog, new LimitOrder("S1", Side.SELL, "AAPL", 100, new BigDecimal("50"), "ACC-MAKER-1"));
        submit(registry, book, tradeLog, new LimitOrder("S2", Side.SELL, "AAPL", 50, new BigDecimal("49"), "ACC-MAKER-2"));

        System.out.println("Book before the market buy: " + book);
        System.out.println("Registry size: " + registry.size());

        // The checkpoint: market BUY 120 walks the asks price-then-time.
        System.out.println("\nSubmitting BUY 120 (market)...");
        submit(registry, book, tradeLog, new MarketOrder("B1", Side.BUY, "AAPL", 120, "ACC-TAKER"));

        System.out.println("\nBook after: " + book);
        System.out.println("Resting ask qty @50: " + book.quantityAt(Side.SELL, new BigDecimal("50")));

        System.out.println("\n--- TradeLog analytics (Streams) ---");
        System.out.println("Volume per symbol: " + tradeLog.totalVolumePerSymbol());
        System.out.println("VWAP per symbol:   " + tradeLog.vwapPerSymbol());
        System.out.println("Top accounts:      " + tradeLog.topNActiveAccounts(3));

        // Cancel-by-id demo on the still-resting remainder of S1.
        System.out.println("\nCancelling S1: " + registry.cancel("S1").map(Order::getStatus).orElse(null));
        book.remove("S1");
        System.out.println("Book after cancel: " + book);
    }

    private static void submit(OrderRegistry registry, OrderBook book, TradeLog tradeLog, Order order) {
        registry.register(order);
        List<Trade> trades = book.match(order);
        tradeLog.recordAll(trades);
        for (Trade t : trades) {
            System.out.println("  TRADE " + t.getQuantity() + " @ " + t.getPrice()
                    + "  (buy " + t.getBuyOrderId() + " / sell " + t.getSellOrderId() + ")");
        }
    }
}
