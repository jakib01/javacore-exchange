package com.exchange.engine;

import com.exchange.domain.Account;
import com.exchange.domain.Instrument;

import java.math.BigDecimal;

/**
 * Builds a pre-seeded {@link Exchange} for the runnable demos ({@code SessionRunner}
 * and {@code ConsoleUI}) so they share one consistent set of instruments and
 * accounts — and so the sample {@code data/orders.csv} can reference them by name.
 *
 * <ul>
 *   <li>Instruments: <b>AAPL</b>, <b>MSFT</b>.</li>
 *   <li>Accounts: <b>ACC1</b> (Alice, cash only), <b>ACC2</b> (Bob, cash + stock),
 *       <b>MM</b> (a market maker with deep cash and inventory to provide
 *       liquidity).</li>
 * </ul>
 */
public final class DemoExchange {

    private DemoExchange() {
    }

    public static Exchange seeded() {
        Exchange exchange = new Exchange();

        exchange.listInstrument(new Instrument("AAPL", "Apple Inc.", new BigDecimal("0.01")));
        exchange.listInstrument(new Instrument("MSFT", "Microsoft Corp.", new BigDecimal("0.01")));

        Account alice = new Account("ACC1", "Alice", new BigDecimal("1000000.00"));

        Account bob = new Account("ACC2", "Bob", new BigDecimal("1000000.00"));
        bob.adjustPosition("AAPL", 1000);
        bob.adjustPosition("MSFT", 1000);

        Account marketMaker = new Account("MM", "MarketMaker", new BigDecimal("100000000.00"));
        marketMaker.adjustPosition("AAPL", 1000000);
        marketMaker.adjustPosition("MSFT", 1000000);

        exchange.openAccount(alice);
        exchange.openAccount(bob);
        exchange.openAccount(marketMaker);

        return exchange;
    }
}
