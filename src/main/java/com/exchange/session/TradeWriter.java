package com.exchange.session;

import com.exchange.domain.Trade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes executed {@link Trade}s out to a CSV file at the end of a session.
 *
 * <p>This is the output half of the I/O lesson:
 * <ul>
 *   <li><b>NIO + try-with-resources.</b> {@link Files#newBufferedWriter} returns a
 *       {@link BufferedWriter} (an {@link AutoCloseable}); the try-with-resources
 *       flushes and closes it for us, even on exception. No manual
 *       {@code finally { writer.close(); }} — which is exactly the boilerplate
 *       try-with-resources was introduced to kill.</li>
 *   <li><b>Explicit charset.</b> We pass {@link StandardCharsets#UTF_8} rather than
 *       relying on the platform default, so the file reads back identically on any
 *       machine.</li>
 *   <li><b>Streaming, not string-building.</b> Rows are written straight to the
 *       buffered writer one at a time; we never accumulate the whole file in a
 *       {@code String} with {@code +=}, which would be the classic O(n&sup2;)
 *       mistake for large outputs.</li>
 * </ul>
 */
public class TradeWriter {

    static final String HEADER =
            "buyOrderId,sellOrderId,buyAccount,sellAccount,symbol,price,quantity,notional,executedAt";

    /**
     * Write {@code trades} to {@code path} (overwriting any existing file).
     *
     * @throws IOException if the file cannot be created or written (checked — the
     *                     disk/permissions are outside our control)
     */
    public void write(Path path, List<Trade> trades) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Trade trade : trades) {
                writer.write(toCsvRow(trade));
                writer.newLine();
            }
        }
    }

    /** One CSV row. {@code String.format} keeps the column order obvious. */
    static String toCsvRow(Trade trade) {
        return String.format("%s,%s,%s,%s,%s,%s,%d,%s,%s",
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                nullToEmpty(trade.getBuyAccountId()),
                nullToEmpty(trade.getSellAccountId()),
                trade.getSymbol(),
                trade.getPrice().toPlainString(),
                trade.getQuantity(),
                trade.getNotionalValue().toPlainString(),
                String.valueOf(trade.getExecutedAt()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
