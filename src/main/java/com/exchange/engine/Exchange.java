package com.exchange.engine;

import com.exchange.book.OrderBook;
import com.exchange.book.OrderRegistry;
import com.exchange.book.TradeLog;
import com.exchange.domain.Account;
import com.exchange.domain.Instrument;
import com.exchange.domain.LimitOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;
import com.exchange.exception.InsufficientFundsException;
import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.UnknownInstrumentException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The Stage&nbsp;3 facade. It stitches together the pieces built in Stages&nbsp;1–2
 * — instruments, accounts, per-symbol {@link OrderBook}s, the {@link OrderRegistry}
 * and the {@link TradeLog} — and adds the one thing those pieces lacked: a single
 * entry point that <b>validates</b>, <b>matches</b> and <b>settles</b> an order,
 * raising the custom exception hierarchy when a business rule is broken.
 *
 * <h2>Where the exceptions come from</h2>
 * <ul>
 *   <li>{@link UnknownInstrumentException} — the order's symbol is not listed.</li>
 *   <li>{@link InvalidOrderException} — the order references an account that does
 *       not exist, or a sell exceeds the seller's holdings (no short selling).</li>
 *   <li>{@link InsufficientFundsException} — a buyer cannot cover the cost, either
 *       caught up-front in {@link #checkAffordability} or
 *       <b>translated</b> from a low-level {@link IllegalStateException} thrown by
 *       {@code Account} during {@link #settle} (see the {@code catch} there).</li>
 * </ul>
 *
 * <p><b>Known simplification:</b> we validate the incoming order's worst-case cost
 * up front but do <em>not</em> reserve cash while a limit order rests, so two
 * resting buys from the same account can in principle over-commit. If that ever
 * surfaces during settlement, the guard in {@code Account} trips and we translate
 * it into an {@link InsufficientFundsException} rather than corrupting state
 * silently. A production venue would reserve at rest time; that is out of scope
 * for this stage, whose focus is the exception model, not the risk engine.
 */
public class Exchange {

    private final Map<String, Instrument> instruments = new LinkedHashMap<>();
    private final Map<String, OrderBook> books = new LinkedHashMap<>();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final OrderRegistry registry = new OrderRegistry();
    private final TradeLog tradeLog = new TradeLog();

    // ── Setup ─────────────────────────────────────────────────────────────────

    /** List a tradable instrument and open its (initially empty) order book. */
    public void listInstrument(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("instrument required");
        }
        instruments.put(instrument.getSymbol(), instrument);
        books.computeIfAbsent(instrument.getSymbol(), OrderBook::new);
    }

    /** Open an account so its orders can settle and its P&L can be queried. */
    public void openAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("account required");
        }
        accounts.put(account.getId(), account);
    }

    // ── The one entry point ─────────────────────────────────────────────────────

    /**
     * Validate, match and settle an order.
     *
     * @return the trades it generated (possibly empty if it rested without crossing)
     * @throws UnknownInstrumentException   symbol is not listed
     * @throws InvalidOrderException        unknown account, or a short sell
     * @throws InsufficientFundsException   buyer cannot afford the order
     */
    public List<Trade> submit(Order order) {
        if (order == null) {
            throw new InvalidOrderException("order required");
        }

        OrderBook book = books.get(order.getSymbol());
        if (book == null) {
            throw new UnknownInstrumentException(order.getSymbol());
        }

        Account submitter = resolveSubmitter(order);
        checkAffordability(order, book, submitter);

        registry.register(order);
        List<Trade> trades = book.match(order);
        for (Trade trade : trades) {
            settle(trade);
        }
        tradeLog.recordAll(trades);
        return trades;
    }

    /** @throws InvalidOrderException if the order names an account we don't know */
    private Account resolveSubmitter(Order order) {
        if (order.getAccountId() == null) {
            return null;   // anonymous / external order — nothing to settle on its side
        }
        Account account = accounts.get(order.getAccountId());
        if (account == null) {
            throw new InvalidOrderException("order " + order.getId()
                    + " references unknown account " + order.getAccountId());
        }
        return account;
    }

    private void checkAffordability(Order order, OrderBook book, Account submitter) {
        if (submitter == null) {
            return;
        }
        if (order.getSide() == Side.SELL) {
            int held = submitter.getPositionFor(order.getSymbol());
            if (held < order.getQuantity()) {
                throw new InvalidOrderException("account " + submitter.getId()
                        + " cannot sell " + order.getQuantity() + " " + order.getSymbol()
                        + " — holds only " + held + " (short selling not supported)");
            }
            return;
        }

        // BUY: worst-case cost. A limit buy never pays above its limit; a market
        // buy is estimated by walking the resting asks it would sweep.
        BigDecimal required = (order instanceof LimitOrder)
                ? ((LimitOrder) order).getLimitPrice().multiply(BigDecimal.valueOf(order.getQuantity()))
                : estimateMarketBuyCost(book, order.getQuantity());

        BigDecimal available = submitter.getAvailableCash();
        if (available.compareTo(required) < 0) {
            throw new InsufficientFundsException(submitter.getId(), required, available);
        }
    }

    /** Sum price&times;qty down the ask side until {@code quantity} is covered. */
    private BigDecimal estimateMarketBuyCost(OrderBook book, int quantity) {
        BigDecimal cost = BigDecimal.ZERO;
        int remaining = quantity;
        for (Map.Entry<BigDecimal, Integer> level : book.depth(Side.SELL).entrySet()) {
            if (remaining <= 0) {
                break;
            }
            int take = Math.min(remaining, level.getValue());
            cost = cost.add(level.getKey().multiply(BigDecimal.valueOf(take)));
            remaining -= take;
        }
        return cost;   // if the book is thin, this only covers the fillable part — which is all that will trade
    }

    /**
     * Move cash and positions for one executed trade. Reuses the {@code Account}
     * primitives from Stage&nbsp;1; if any of their guards trip (which would mean a
     * race past our up-front checks) we <b>translate</b> the low-level
     * {@link IllegalStateException} into a domain exception, chaining the original
     * as the cause so the stack trace survives.
     */
    private void settle(Trade trade) {
        Account buyer = accounts.get(trade.getBuyAccountId());
        Account seller = accounts.get(trade.getSellAccountId());
        BigDecimal notional = trade.getNotionalValue();
        try {
            if (buyer != null) {
                buyer.reserveCash(notional);     // guards available cash
                buyer.debitReserved(notional);
                buyer.adjustPosition(trade.getSymbol(), trade.getQuantity());
            }
            if (seller != null) {
                seller.adjustPosition(trade.getSymbol(), -trade.getQuantity());   // guards short selling
                seller.credit(notional);
            }
        } catch (IllegalStateException e) {
            throw new InsufficientFundsException("settlement failed for trade "
                    + trade.getBuyOrderId() + "/" + trade.getSellOrderId() + ": " + e.getMessage(), e);
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────────

    /** @throws UnknownInstrumentException if the symbol is not listed */
    public OrderBook book(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            throw new UnknownInstrumentException(symbol);
        }
        return book;
    }

    /**
     * Build a {@link PnlReport} for an account, marking open positions to each
     * symbol's last traded price.
     *
     * @throws InvalidOrderException if no such account is open
     */
    public PnlReport pnl(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new InvalidOrderException("unknown account: " + accountId);
        }

        Map<String, Integer> positions = new TreeMap<>(account.getPositions());   // sorted for stable output
        Map<String, BigDecimal> marks = new LinkedHashMap<>();
        BigDecimal positionValue = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : positions.entrySet()) {
            BigDecimal mark = tradeLog.lastPrice(entry.getKey()).orElse(BigDecimal.ZERO);
            marks.put(entry.getKey(), mark);
            positionValue = positionValue.add(mark.multiply(BigDecimal.valueOf(entry.getValue())));
        }

        BigDecimal equity = account.getCashBalance().add(positionValue);
        BigDecimal pnl = equity.subtract(account.getInitialCash());
        return new PnlReport(account.getId(), account.getOwner(), account.getInitialCash(),
                account.getCashBalance(), account.getReservedCash(), positions, marks, equity, pnl);
    }

    /**
     * Cancel a resting order: drop it from the registry and pull it off its book.
     *
     * @return the cancelled order, or empty if unknown / already terminal
     */
    public Optional<Order> cancel(String orderId) {
        Optional<Order> cancelled = registry.cancel(orderId);
        cancelled.ifPresent(order -> {
            OrderBook book = books.get(order.getSymbol());
            if (book != null) {
                book.remove(orderId);
            }
        });
        return cancelled;
    }

    public boolean isListed(String symbol) {
        return instruments.containsKey(symbol);
    }

    public Collection<Instrument> instruments() {
        return Collections.unmodifiableCollection(instruments.values());
    }

    public Collection<Account> accounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public Optional<Account> account(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    public TradeLog tradeLog() {
        return tradeLog;
    }

    public OrderRegistry registry() {
        return registry;
    }
}
