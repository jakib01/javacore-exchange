package com.exchange.session;

import com.exchange.domain.Order;
import com.exchange.domain.Trade;
import com.exchange.engine.DemoExchange;
import com.exchange.engine.Exchange;
import com.exchange.exception.InsufficientFundsException;
import com.exchange.exception.InvalidOrderException;
import com.exchange.exception.UnknownInstrumentException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch entry point: load {@code data/orders.csv}, run every order through a
 * seeded {@link Exchange}, and write the resulting fills to {@code data/trades.csv}.
 *
 * <p>Run it with:
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.exchange.session.SessionRunner
 * </pre>
 *
 * <p>It is the showcase for Stage&nbsp;3's error handling:
 * <ul>
 *   <li><b>try-with-resources</b> lives inside {@code SessionLoader}/{@code TradeWriter}.</li>
 *   <li><b>checked vs unchecked</b>: a missing file ({@link IOException}) is caught
 *       in {@code main}; a malformed line ({@link InvalidOrderException}) and the
 *       per-order business failures are caught where they can be acted on.</li>
 *   <li><b>multi-catch</b>: the three sibling domain failures share one handler so
 *       a single bad order is rejected without aborting the whole session.</li>
 *   <li><b>chaining</b>: when a parse failure carries a cause, we print it too.</li>
 * </ul>
 */
public class SessionRunner {

    private static final Path DEFAULT_INPUT = Path.of("data", "orders.csv");
    private static final Path DEFAULT_OUTPUT = Path.of("data", "trades.csv");

    public static void main(String[] args) {
        Path input = (args.length >= 1) ? Path.of(args[0]) : DEFAULT_INPUT;
        Path output = (args.length >= 2) ? Path.of(args[1]) : DEFAULT_OUTPUT;

        try {
            run(input, output);
        } catch (NoSuchFileException e) {
            // A more specific IOException — worth its own friendlier message.
            System.err.println("No orders file at " + e.getFile() + " (run from the project root).");
        } catch (IOException e) {
            System.err.println("I/O failure: " + e.getMessage());
        }
    }

    /** The session pipeline. Returns the trades produced (handy for tests). */
    static List<Trade> run(Path input, Path output) throws IOException {
        Exchange exchange = DemoExchange.seeded();
        SessionLoader loader = new SessionLoader();

        List<Order> orders;
        try {
            orders = loader.load(input);
        } catch (InvalidOrderException e) {
            // The stream-based loader short-circuits on the first bad line, so a
            // malformed file yields no orders at all — fatal to the batch.
            System.err.println("Orders file is malformed: " + e.getMessage());
            printCause(e);
            return List.of();
        }

        System.out.println("Loaded " + orders.size() + " order(s) from " + input);
        System.out.println("------------------------------------------------------------");

        List<Trade> allTrades = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;
        for (Order order : orders) {
            try {
                List<Trade> trades = exchange.submit(order);
                allTrades.addAll(trades);
                accepted++;
                System.out.printf("OK      %-7s %-6s %-4s qty %-7d -> %d trade(s)%n",
                        order.getId(), order.getSide(), order.getSymbol(),
                        order.getQuantity(), trades.size());
            } catch (UnknownInstrumentException | InsufficientFundsException | InvalidOrderException e) {
                // multi-catch: three sibling failures, one recovery path — keep going.
                rejected++;
                System.out.printf("REJECT  %-7s %s%n", order.getId(), e.getMessage());
            }
        }

        new TradeWriter().write(output, allTrades);

        System.out.println("------------------------------------------------------------");
        System.out.printf("Session complete: %d accepted, %d rejected, %d trade(s) -> %s%n",
                accepted, rejected, allTrades.size(), output);
        return allTrades;
    }

    private static void printCause(Throwable t) {
        Throwable cause = t.getCause();
        if (cause != null) {
            System.err.println("  caused by " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }
    }
}
