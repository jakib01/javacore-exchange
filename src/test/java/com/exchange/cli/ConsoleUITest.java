package com.exchange.cli;

import com.exchange.engine.DemoExchange;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the REPL with canned input and inspects the captured output. The headline
 * requirement under test: <b>no input, however malformed, crashes the loop.</b>
 */
class ConsoleUITest {

    /** Run a script through a fresh seeded console and return everything it printed. */
    private String run(String script) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        ConsoleUI ui = new ConsoleUI(DemoExchange.seeded(), out);
        ui.run(new Scanner(script));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void badNumberIsReportedNotThrown() {
        String output = run("buy AAPL 100 notanumber\nquit\n");
        assertTrue(output.contains("bad input"), output);
        assertTrue(output.contains("bye."), "loop must survive and reach quit");
    }

    @Test
    void unknownCommandIsReported() {
        String output = run("frobnicate\nquit\n");
        assertTrue(output.contains("unknown command"));
    }

    @Test
    void domainErrorIsReportedCleanly() {
        // ACC1 (default) holds no AAPL, so this sell is a short sale.
        String output = run("sell AAPL 10 190.00\nquit\n");
        assertTrue(output.contains("short selling"), output);
        assertFalse(output.contains("Exception"), "no raw stack trace should leak to the user");
    }

    @Test
    void fullTradeFlowPrintsTheFill() {
        String output = run("""
                login MM
                sell AAPL 100 190.00
                login ACC1
                buy AAPL 100 190.00
                quit
                """);
        assertTrue(output.contains("now trading as MM"));
        assertTrue(output.contains("TRADE 100 AAPL @ 190.00"), output);
    }

    @Test
    void bookCommandRendersBothSides() {
        String output = run("""
                login MM
                sell AAPL 50 191.00
                buy AAPL 40 188.00
                book AAPL
                quit
                """);
        assertTrue(output.contains("ASKS"));
        assertTrue(output.contains("BIDS"));
        assertTrue(output.contains("191.00"));
        assertTrue(output.contains("188.00"));
    }

    @Test
    void pnlCommandRenders() {
        String output = run("pnl ACC1\nquit\n");
        assertTrue(output.contains("P&L for ACC1"));
        assertTrue(output.contains("equity"));
    }

    @Test
    void unknownPnlAccountIsReported() {
        String output = run("pnl GHOST\nquit\n");
        assertTrue(output.contains("unknown account"));
    }

    @Test
    void marketKeywordIsAcceptedAsPrice() {
        String output = run("""
                login MM
                sell AAPL 30 190.00
                login ACC1
                buy AAPL 30 market
                quit
                """);
        assertTrue(output.contains("TRADE 30 AAPL @ 190.00"), output);
    }
}
