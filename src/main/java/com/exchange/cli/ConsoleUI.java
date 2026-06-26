package com.exchange.cli;

import com.exchange.book.OrderBook;
import com.exchange.domain.Account;
import com.exchange.domain.Instrument;
import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.domain.Trade;
import com.exchange.engine.DemoExchange;
import com.exchange.engine.Exchange;
import com.exchange.exception.ExchangeException;
import com.exchange.exception.InvalidOrderException;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * An interactive REPL over an {@link Exchange}, driven by a {@link Scanner}.
 *
 * <p>The Stage&nbsp;3 brief for this piece is "parse, validate, report errors
 * cleanly <b>without crashing</b>". The design that delivers that:
 * <ul>
 *   <li>{@link #run(Scanner)} reads a line, dispatches it, and wraps the dispatch
 *       in a two-arm catch — domain failures ({@link ExchangeException}) get a
 *       business message, everything else ({@code NumberFormatException} from a
 *       bad price, wrong argument counts) is reported as bad input. Either way the
 *       loop continues to the next command.</li>
 *   <li>It is constructed with its {@link Exchange} and a {@link PrintStream}, and
 *       {@code run} takes the {@code Scanner}, so a test can feed canned input and
 *       capture the output (see {@code ConsoleUITest}).</li>
 * </ul>
 *
 * <h2>Commands</h2>
 * <pre>
 *   buy  SYMBOL QTY PRICE     limit buy as the active account (PRICE may be 'market')
 *   sell SYMBOL QTY PRICE     limit sell as the active account
 *   book SYMBOL               show resting depth for a symbol
 *   pnl  ACCOUNT              mark-to-market profit/loss for an account
 *   cancel ORDER_ID           cancel a resting order
 *   login ACCOUNT             switch the active trading account
 *   accounts | instruments | trades | who | help | quit
 * </pre>
 */
public class ConsoleUI {

    private final Exchange exchange;
    private final PrintStream out;

    private String activeAccount;
    private int orderSequence = 0;

    public ConsoleUI(Exchange exchange, PrintStream out) {
        this.exchange = exchange;
        this.out = out;
        // Default the active trader to the first open account, if any.
        this.activeAccount = exchange.accounts().stream()
                .map(Account::getId).findFirst().orElse(null);
    }

    public static void main(String[] args) {
        ConsoleUI ui = new ConsoleUI(DemoExchange.seeded(), System.out);
        // try-with-resources: the Scanner wraps System.in and is closed on exit.
        try (Scanner scanner = new Scanner(System.in)) {
            ui.run(scanner);
        }
    }

    public void run(Scanner scanner) {
        printWelcome();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().strip();
            if (line.isEmpty()) {
                continue;
            }
            if (isQuit(line)) {
                out.println("bye.");
                return;
            }
            try {
                dispatch(line);
            } catch (ExchangeException e) {
                // Domain rule broken — report cleanly, keep the prompt alive.
                out.println("  ! " + e.getMessage());
            } catch (RuntimeException e) {
                // Parse/usage error (bad number, missing argument, ...). Never fatal.
                out.println("  ! bad input: " + e.getMessage());
            }
        }
    }

    private void dispatch(String line) {
        String[] tokens = line.split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);
        switch (command) {
            case "buy"         -> placeOrder(Side.BUY, tokens);
            case "sell"        -> placeOrder(Side.SELL, tokens);
            case "book"        -> showBook(tokens);
            case "pnl"         -> showPnl(tokens);
            case "cancel"      -> cancel(tokens);
            case "login"       -> login(tokens);
            case "who"         -> out.println("  active account: " + activeAccount);
            case "accounts"    -> showAccounts();
            case "instruments" -> showInstruments();
            case "trades"      -> showTrades();
            case "help", "?"   -> printHelp();
            default            -> out.println("  ! unknown command '" + command + "' — type 'help'");
        }
    }

    // ── Commands ─────────────────────────────────────────────────────────────────

    private void placeOrder(Side side, String[] tokens) {
        if (tokens.length != 4) {
            throw new InvalidOrderException("usage: " + tokens[0] + " SYMBOL QTY PRICE   (PRICE may be 'market')");
        }
        if (activeAccount == null) {
            throw new InvalidOrderException("no active account — use 'login ACCOUNT' first");
        }
        String symbol = tokens[1].toUpperCase(Locale.ROOT);
        int quantity = Integer.parseInt(tokens[2]);   // NumberFormatException -> caught as bad input
        String priceText = tokens[3];

        String id = "UI-" + (++orderSequence);
        Order order = isMarket(priceText)
                ? new MarketOrder(id, side, symbol, quantity, activeAccount)
                : new LimitOrder(id, side, symbol, quantity, new BigDecimal(priceText), activeAccount);

        List<Trade> trades = exchange.submit(order);
        report(order, trades);
    }

    private void report(Order order, List<Trade> trades) {
        if (trades.isEmpty()) {
            out.println("  rested " + order.getId() + " (" + order.getRemainingQuantity()
                    + " open, no match)");
            return;
        }
        for (Trade trade : trades) {
            out.printf("  TRADE %d %s @ %s%n",
                    trade.getQuantity(), trade.getSymbol(), trade.getPrice().toPlainString());
        }
        if (order.getRemainingQuantity() > 0) {
            out.println("  " + order.getRemainingQuantity() + " left "
                    + (order instanceof LimitOrder ? "resting" : "unfilled"));
        }
    }

    private void showBook(String[] tokens) {
        if (tokens.length != 2) {
            throw new InvalidOrderException("usage: book SYMBOL");
        }
        String symbol = tokens[1].toUpperCase(Locale.ROOT);
        OrderBook book = exchange.book(symbol);   // throws UnknownInstrumentException if not listed
        out.println(renderBook(symbol, book));
    }

    /** Build the depth ladder. Demonstrates StringBuilder-in-a-loop (not {@code +=}). */
    private String renderBook(String symbol, OrderBook book) {
        Map<BigDecimal, Integer> asks = book.depth(Side.SELL);   // ascending (best/lowest first)
        Map<BigDecimal, Integer> bids = book.depth(Side.BUY);    // descending (best/highest first)

        StringBuilder sb = new StringBuilder();
        sb.append(symbol).append(" order book\n");
        sb.append("  ASKS (sell)\n");
        if (asks.isEmpty()) {
            sb.append("    (none)\n");
        } else {
            // Reverse so the best ask sits just above the spread line.
            List<Map.Entry<BigDecimal, Integer>> askLevels = new java.util.ArrayList<>(asks.entrySet());
            for (int i = askLevels.size() - 1; i >= 0; i--) {
                Map.Entry<BigDecimal, Integer> level = askLevels.get(i);
                sb.append(String.format("    %-10s x %d%n", level.getKey().toPlainString(), level.getValue()));
            }
        }
        sb.append("  ----------------\n");
        sb.append("  BIDS (buy)\n");
        if (bids.isEmpty()) {
            sb.append("    (none)\n");
        } else {
            for (Map.Entry<BigDecimal, Integer> level : bids.entrySet()) {
                sb.append(String.format("    %-10s x %d%n", level.getKey().toPlainString(), level.getValue()));
            }
        }
        return sb.toString().stripTrailing();
    }

    private void showPnl(String[] tokens) {
        if (tokens.length != 2) {
            throw new InvalidOrderException("usage: pnl ACCOUNT");
        }
        out.println(exchange.pnl(tokens[1]).format());   // throws InvalidOrderException if unknown
    }

    private void cancel(String[] tokens) {
        if (tokens.length != 2) {
            throw new InvalidOrderException("usage: cancel ORDER_ID");
        }
        exchange.cancel(tokens[1])
                .ifPresentOrElse(
                        o -> out.println("  cancelled " + o.getId()),
                        () -> out.println("  nothing to cancel for '" + tokens[1] + "'"));
    }

    private void login(String[] tokens) {
        if (tokens.length != 2) {
            throw new InvalidOrderException("usage: login ACCOUNT");
        }
        String accountId = tokens[1];
        if (exchange.account(accountId).isEmpty()) {
            throw new InvalidOrderException("no such account: " + accountId);
        }
        activeAccount = accountId;
        out.println("  now trading as " + activeAccount);
    }

    private void showAccounts() {
        for (Account account : exchange.accounts()) {
            out.printf("  %-5s %-12s cash %s  positions %s%n",
                    account.getId(), account.getOwner(),
                    account.getCashBalance().toPlainString(), account.getPositions());
        }
    }

    private void showInstruments() {
        for (Instrument instrument : exchange.instruments()) {
            out.printf("  %-6s %s%n", instrument.getSymbol(), instrument.getName());
        }
    }

    private void showTrades() {
        int count = exchange.tradeLog().size();
        out.println("  " + count + " trade(s) so far");
        out.println("  volume/symbol: " + exchange.tradeLog().totalVolumePerSymbol());
        out.println("  vwap/symbol:   " + exchange.tradeLog().vwapPerSymbol());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static boolean isMarket(String priceText) {
        return priceText.equalsIgnoreCase("market") || priceText.equalsIgnoreCase("mkt");
    }

    private static boolean isQuit(String line) {
        String word = line.toLowerCase(Locale.ROOT);
        return word.equals("quit") || word.equals("exit") || word.equals("q");
    }

    private void printWelcome() {
        out.println("""
                ── javacore-exchange console ───────────────────────────────
                Type 'help' for commands, 'quit' to leave.""");
        out.println("Active account: " + activeAccount);
    }

    private void printHelp() {
        // Text block: the help screen reads as-is, no '\n' clutter.
        out.println("""
                commands:
                  buy  SYMBOL QTY PRICE    limit buy  (PRICE may be 'market')
                  sell SYMBOL QTY PRICE    limit sell (PRICE may be 'market')
                  book SYMBOL              show resting depth
                  pnl  ACCOUNT             mark-to-market P&L for an account
                  cancel ORDER_ID          cancel a resting order
                  login ACCOUNT            switch the active trading account
                  who                      show the active account
                  accounts                 list accounts
                  instruments              list instruments
                  trades                   trade count + volume/VWAP analytics
                  help | quit""");
    }
}
