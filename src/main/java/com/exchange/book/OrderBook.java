package com.exchange.book;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A price-time-priority order book for a single instrument.
 *
 * <h2>Why these data structures (this is the whole Stage 2 lesson)</h2>
 *
 * <p><b>Price priority</b> comes from {@link TreeMap}: a red-black tree keyed by
 * price, so {@link TreeMap#firstEntry()} is always the best level in O(log n).
 * <ul>
 *   <li>{@code asks} use <em>natural</em> order → {@code firstKey()} is the
 *       <em>lowest</em> sell price (the best ask).</li>
 *   <li>{@code bids} use {@link Comparator#reverseOrder()} → {@code firstKey()}
 *       is the <em>highest</em> buy price (the best bid).</li>
 * </ul>
 *
 * <p><b>Time priority</b> comes from {@link ArrayDeque}: at each price level the
 * resting orders sit in a FIFO queue. New orders join the tail
 * ({@code addLast}); matching consumes the head ({@code peekFirst}/
 * {@code pollFirst}). So the order that arrived first at a price trades first.
 *
 * <p>Combine the two and price-time priority falls out of the structure — there
 * is no explicit "sort by price then time" anywhere in the matching loop.
 *
 * <h2>A note on {@code BigDecimal} keys</h2>
 * <p>{@code TreeMap} decides key identity with {@code compareTo}, not
 * {@code equals}. For {@code BigDecimal} that is exactly what we want:
 * {@code new BigDecimal("50")} and {@code new BigDecimal("50.00")} are
 * {@code compareTo}-equal (though {@code equals}-unequal because of scale), so
 * they collapse onto a single price level instead of silently splitting it.
 */
public class OrderBook {

    private final String symbol;

    // price -> FIFO queue of resting orders at that price
    private final TreeMap<BigDecimal, Deque<LimitOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, Deque<LimitOrder>> asks = new TreeMap<>();

    public OrderBook(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol required");
        }
        this.symbol = symbol;
    }

    /**
     * Submit an incoming order. It is matched against the opposite side using
     * price-time priority, generating {@link Trade}s for every fill. Any unfilled
     * remainder of a {@link LimitOrder} rests in the book; a market order's
     * remainder does not rest (there is no price to rest it at).
     *
     * @return the trades generated, in execution order (possibly empty)
     */
    public List<Trade> match(Order incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("incoming order required");
        }
        if (!symbol.equals(incoming.getSymbol())) {
            throw new IllegalArgumentException("order symbol " + incoming.getSymbol()
                    + " does not belong to book " + symbol);
        }

        // A buy crosses the asks (sells); a sell crosses the bids (buys).
        TreeMap<BigDecimal, Deque<LimitOrder>> oppositeSide =
                incoming.getSide() == Side.BUY ? asks : bids;

        List<Trade> trades = new ArrayList<>();

        // Walk the opposite side from its best level outward, filling while we can.
        while (incoming.getRemainingQuantity() > 0 && !oppositeSide.isEmpty()) {
            Map.Entry<BigDecimal, Deque<LimitOrder>> bestLevel = oppositeSide.firstEntry();
            BigDecimal levelPrice = bestLevel.getKey();

            // Crossable? A limit order stops once the best opposite price is
            // worse than its limit. A market order always crosses.
            if (!incoming.isMatchable(levelPrice)) {
                break;
            }

            Deque<LimitOrder> queue = bestLevel.getValue();
            while (incoming.getRemainingQuantity() > 0 && !queue.isEmpty()) {
                LimitOrder resting = queue.peekFirst();        // FIFO: oldest first
                int fillQty = Math.min(incoming.getRemainingQuantity(), resting.getRemainingQuantity());

                // Trade always prints at the resting (passive) order's price.
                trades.add(buildTrade(incoming, resting, levelPrice, fillQty));

                incoming.fill(fillQty);
                resting.fill(fillQty);

                if (resting.isFullyFilled()) {
                    queue.pollFirst();                         // done — remove from the front
                }
            }

            if (queue.isEmpty()) {
                oppositeSide.pollFirstEntry();                 // remove the now-empty price level
            }
        }

        restRemainder(incoming);
        return trades;
    }

    private Trade buildTrade(Order incoming, LimitOrder resting, BigDecimal price, int qty) {
        Order buy = incoming.getSide() == Side.BUY ? incoming : resting;
        Order sell = incoming.getSide() == Side.SELL ? incoming : resting;
        return new Trade(
                buy.getId(), sell.getId(),
                buy.getAccountId(), sell.getAccountId(),
                symbol, price, qty, Instant.now());
    }

    /** Rest the unfilled remainder of a limit order on its own side of the book. */
    private void restRemainder(Order incoming) {
        if (incoming.getRemainingQuantity() <= 0) {
            return;
        }
        if (!(incoming instanceof LimitOrder)) {
            // Market orders never rest. In a production venue the unfilled
            // remainder would be cancelled/expired; here we simply leave it
            // off-book with whatever PARTIALLY_FILLED/NEW status it carries.
            return;
        }
        LimitOrder resting = (LimitOrder) incoming;
        TreeMap<BigDecimal, Deque<LimitOrder>> ownSide =
                resting.getSide() == Side.BUY ? bids : asks;
        ownSide.computeIfAbsent(resting.getLimitPrice(), k -> new ArrayDeque<>())
                .addLast(resting);
    }

    /** Best (highest) bid price, or empty if there are no bids. */
    public Optional<BigDecimal> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    /** Best (lowest) ask price, or empty if there are no asks. */
    public Optional<BigDecimal> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    /** Total open quantity resting at a given price on the given side. */
    public int quantityAt(Side side, BigDecimal price) {
        Deque<LimitOrder> queue = (side == Side.BUY ? bids : asks).get(price);
        if (queue == null) {
            return 0;
        }
        int total = 0;
        for (LimitOrder o : queue) {
            total += o.getRemainingQuantity();
        }
        return total;
    }

    /**
     * Remove a still-resting limit order from the book by id (used by cancel).
     *
     * @return {@code true} if the order was found and removed
     */
    public boolean remove(String orderId) {
        return removeFromSide(bids, orderId) || removeFromSide(asks, orderId);
    }

    private boolean removeFromSide(TreeMap<BigDecimal, Deque<LimitOrder>> side, String orderId) {
        for (Map.Entry<BigDecimal, Deque<LimitOrder>> level : side.entrySet()) {
            Deque<LimitOrder> queue = level.getValue();
            // removeIf uses the deque's own iterator internally — safe removal
            // while traversing (no ConcurrentModificationException).
            boolean removed = queue.removeIf(o -> o.getId().equals(orderId));
            if (removed) {
                if (queue.isEmpty()) {
                    side.remove(level.getKey());
                }
                return true;
            }
        }
        return false;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    @Override
    public String toString() {
        return "OrderBook{symbol=" + symbol
                + ", bestBid=" + bestBid().map(Object::toString).orElse("-")
                + ", bestAsk=" + bestAsk().map(Object::toString).orElse("-") + "}";
    }
}
