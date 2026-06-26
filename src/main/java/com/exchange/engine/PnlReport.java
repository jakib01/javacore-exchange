package com.exchange.engine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.StringJoiner;

/**
 * An immutable snapshot of one account's profit-and-loss, computed by
 * {@link Exchange#pnl(String)}.
 *
 * <p>Definitions used here:
 * <ul>
 *   <li><b>market value</b> of a position = quantity &times; the symbol's last
 *       traded price (the "mark").</li>
 *   <li><b>equity</b> = cash balance + &Sigma; market value of all positions.</li>
 *   <li><b>P&amp;L</b> = equity &minus; the account's initial cash. Buying spends
 *       cash but gains an equal-valued position, so a flat market leaves P&amp;L at
 *       zero; P&amp;L moves only as marks move.</li>
 * </ul>
 *
 * <p>This class also shows off two Stage&nbsp;3 string ideas: {@link String#format}
 * for aligned numeric columns, and a <b>text block</b> ({@code """ ... """}) for
 * the multi-line template instead of a pile of {@code "\n"}-glued concatenations.
 */
public final class PnlReport {

    private final String accountId;
    private final String owner;
    private final BigDecimal initialCash;
    private final BigDecimal cashBalance;
    private final BigDecimal reservedCash;
    private final Map<String, Integer> positions;     // symbol -> quantity held
    private final Map<String, BigDecimal> marks;       // symbol -> last traded price
    private final BigDecimal equity;
    private final BigDecimal pnl;

    PnlReport(String accountId, String owner, BigDecimal initialCash, BigDecimal cashBalance,
              BigDecimal reservedCash, Map<String, Integer> positions, Map<String, BigDecimal> marks,
              BigDecimal equity, BigDecimal pnl) {
        this.accountId = accountId;
        this.owner = owner;
        this.initialCash = initialCash;
        this.cashBalance = cashBalance;
        this.reservedCash = reservedCash;
        this.positions = positions;
        this.marks = marks;
        this.equity = equity;
        this.pnl = pnl;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getEquity() {
        return equity;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public Map<String, Integer> getPositions() {
        return positions;
    }

    /** A human-readable, aligned multi-line render for the console. */
    public String format() {
        StringJoiner lines = new StringJoiner("\n");
        if (positions.isEmpty()) {
            lines.add("    (no open positions)");
        } else {
            positions.forEach((symbol, qty) -> {
                BigDecimal mark = marks.getOrDefault(symbol, BigDecimal.ZERO);
                BigDecimal value = mark.multiply(BigDecimal.valueOf(qty));
                lines.add(String.format("    %-6s %6d @ %-10s = %12s",
                        symbol, qty, mark.toPlainString(), value.toPlainString()));
            });
        }

        // Text block (Java 15+): the layout is the literal, no "\n" noise.
        return String.format("""
                P&L for %s (%s)
                  cash balance : %s   (reserved %s)
                  positions:
                %s
                  equity       : %s
                  P&L vs start : %s   (started with %s)""",
                accountId, owner,
                cashBalance.toPlainString(), reservedCash.toPlainString(),
                lines.toString(),
                equity.toPlainString(),
                pnl.toPlainString(), initialCash.toPlainString());
    }

    @Override
    public String toString() {
        return "PnlReport{account=" + accountId + ", equity=" + equity + ", pnl=" + pnl + "}";
    }
}
