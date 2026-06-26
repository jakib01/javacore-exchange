package com.exchange.book;

import com.exchange.domain.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An append-only journal of executed {@link Trade}s, with stream-based analytics.
 *
 * <p>This is the Streams deep-dive for Stage 2: every query below is a
 * read-only pipeline over the trade list — no manual loops, no mutable
 * accumulators. The pipelines stay lazy until a terminal {@code collect}/
 * {@code reduce}/{@code sum} runs them.
 */
public class TradeLog {

    private final List<Trade> trades = new ArrayList<>();

    /** Scale used when dividing to compute a volume-weighted average price. */
    private static final int VWAP_SCALE = 8;

    public void record(Trade trade) {
        if (trade == null) {
            throw new IllegalArgumentException("trade required");
        }
        trades.add(trade);
    }

    public void recordAll(List<Trade> batch) {
        if (batch != null) {
            batch.forEach(this::record);
        }
    }

    /** Unmodifiable view of every trade in execution order. */
    public List<Trade> getTrades() {
        return Collections.unmodifiableList(trades);
    }

    /**
     * Total traded quantity per symbol.
     *
     * <p>Classic {@code groupingBy} + {@code summingLong}: group by symbol, and
     * for each group sum the quantities into a {@code long} (volume can overflow
     * {@code int} once the book gets busy).
     */
    public Map<String, Long> totalVolumePerSymbol() {
        return trades.stream()
                .collect(Collectors.groupingBy(
                        Trade::getSymbol,
                        Collectors.summingLong(Trade::getQuantity)));
    }

    /**
     * Volume-weighted average price for one symbol: {@code Σ(price·qty) / Σ(qty)}.
     *
     * @return empty if there are no trades for the symbol
     */
    public Optional<BigDecimal> vwap(String symbol) {
        List<Trade> forSymbol = trades.stream()
                .filter(t -> t.getSymbol().equals(symbol))
                .collect(Collectors.toList());
        if (forSymbol.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal notional = forSymbol.stream()
                .map(Trade::getNotionalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long quantity = forSymbol.stream()
                .mapToLong(Trade::getQuantity)
                .sum();
        return Optional.of(notional.divide(BigDecimal.valueOf(quantity), VWAP_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * VWAP for every symbol in a single grouped pass.
     *
     * <p>Uses {@link Collectors#teeing} (Java 12+): each group is fed to two
     * downstream collectors at once — one summing notional, one summing
     * quantity — and the merger divides them into the VWAP. One traversal, no
     * intermediate per-symbol lists.
     */
    public Map<String, BigDecimal> vwapPerSymbol() {
        return trades.stream()
                .collect(Collectors.groupingBy(
                        Trade::getSymbol,
                        Collectors.teeing(
                                Collectors.reducing(BigDecimal.ZERO, Trade::getNotionalValue, BigDecimal::add),
                                Collectors.summingLong(Trade::getQuantity),
                                (notional, qty) -> qty == 0
                                        ? BigDecimal.ZERO
                                        : notional.divide(BigDecimal.valueOf(qty), VWAP_SCALE, RoundingMode.HALF_UP))));
    }

    /**
     * The {@code n} accounts that participated in the most trades, busiest first.
     *
     * <p>Each trade touches two accounts (buyer + seller), so we
     * {@code flatMap} both ids out of every trade, drop nulls (Stage 1 trades
     * carry no account), then {@code groupingBy} + {@code counting}. Finally we
     * sort the entries by count descending and {@code limit(n)}.
     *
     * @return immutable (accountId, tradeCount) pairs, at most {@code n} of them
     */
    public List<Map.Entry<String, Long>> topNActiveAccounts(int n) {
        if (n <= 0) {
            return List.of();
        }
        Map<String, Long> tradesPerAccount = trades.stream()
                .flatMap(t -> Stream.of(t.getBuyAccountId(), t.getSellAccountId()))
                .filter(id -> id != null)
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        return tradesPerAccount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))   // stable tie-break by id
                .limit(n)
                .map(e -> Map.entry(e.getKey(), e.getValue()))         // immutable copies
                .collect(Collectors.toList());
    }

    public int size() {
        return trades.size();
    }
}
