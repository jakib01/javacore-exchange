package com.exchange.session;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.exception.InvalidOrderException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads a session's orders from a CSV file into {@link Order} objects.
 *
 * <h2>The CSV shape</h2>
 * <pre>
 *   id,type,side,symbol,quantity,price[,account]
 *   ORD-1,LIMIT,SELL,AAPL,100,190.00,MM
 *   ORD-2,MARKET,BUY,AAPL,120,,ACC1
 * </pre>
 * Blank lines and lines starting with {@code #} are ignored, so the header can
 * live as a comment. {@code price} is required for {@code LIMIT} and left empty
 * for {@code MARKET}; {@code account} is optional.
 *
 * <h2>Two kinds of error, two kinds of exception (the Stage 3 point)</h2>
 * <ul>
 *   <li>{@link #load(Path)} declares <b>checked</b> {@link IOException}: the file
 *       being missing or unreadable is an <em>environmental</em> condition the
 *       caller genuinely must plan for, so the compiler insists they handle it.</li>
 *   <li>{@link #parseLine(String)} throws <b>unchecked</b>
 *       {@link InvalidOrderException}: a garbled field is a data error, and we
 *       <em>translate</em> the low-level {@link NumberFormatException} /
 *       {@link IllegalArgumentException} into a domain exception that names the
 *       offending line, chaining the original as the {@code cause}.</li>
 * </ul>
 *
 * <h2>Why {@code Files.lines}</h2>
 * <p>{@link Files#lines(Path, java.nio.charset.Charset)} returns a lazy
 * {@link Stream} backed by the open file, and {@code Stream} is
 * {@link AutoCloseable} — so it goes in a <b>try-with-resources</b>. The whole
 * parse is one lazy pipeline; the file is read line-by-line (not slurped whole)
 * and the underlying handle is closed automatically, even if {@code parseLine}
 * throws partway through.
 */
public class SessionLoader {

    private static final String COMMENT_PREFIX = "#";

    /**
     * Parse every order line in {@code path}.
     *
     * @throws IOException             if the file cannot be opened or read (checked)
     * @throws InvalidOrderException   if any non-comment line is malformed (unchecked)
     */
    public List<Order> load(Path path) throws IOException {
        // try-with-resources: the Stream (and the file handle behind it) is closed
        // on the way out — normal return OR exception.
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT_PREFIX))
                    .map(SessionLoader::parseLine)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Parse one CSV line into an {@link Order}. Package-private so tests can hit it
     * directly without going through a file.
     */
    static Order parseLine(String line) {
        // split(",", -1) keeps trailing empties, so a missing price/account is a
        // real empty field, not a dropped column.
        String[] fields = line.split(",", -1);
        if (fields.length < 6) {
            throw new InvalidOrderException(
                    "expected id,type,side,symbol,quantity,price[,account] but found "
                            + fields.length + " field(s): " + line);
        }

        String id = fields[0].strip();
        String typeText = fields[1].strip().toUpperCase(Locale.ROOT);
        String sideText = fields[2].strip().toUpperCase(Locale.ROOT);
        String symbol = fields[3].strip();
        String quantityText = fields[4].strip();
        String priceText = fields[5].strip();
        String accountId = (fields.length >= 7) ? emptyToNull(fields[6].strip()) : null;

        Side side = parseSide(sideText, line);
        int quantity = parseQuantity(quantityText, line);

        try {
            switch (typeText) {
                case "LIMIT":
                    return new LimitOrder(id, side, symbol, quantity, parsePrice(priceText, line), accountId);
                case "MARKET":
                    return new MarketOrder(id, side, symbol, quantity, accountId);
                default:
                    throw new InvalidOrderException("unknown order type '" + typeText
                            + "' (expected LIMIT or MARKET): " + line);
            }
        } catch (IllegalArgumentException e) {
            // The domain constructors reject empty id/symbol, qty<=0, price<=0 with
            // IllegalArgumentException. Translate into our hierarchy, keeping the
            // cause. (parsePrice/parseSide already throw InvalidOrderException — not
            // an IllegalArgumentException — so they slip past this catch unwrapped.)
            throw new InvalidOrderException("invalid order fields in: " + line, e);
        }
    }

    private static Side parseSide(String sideText, String line) {
        try {
            return Side.valueOf(sideText);
        } catch (IllegalArgumentException e) {
            throw new InvalidOrderException("bad side '" + sideText + "' (expected BUY or SELL): " + line, e);
        }
    }

    private static int parseQuantity(String quantityText, String line) {
        try {
            return Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            throw new InvalidOrderException("bad quantity '" + quantityText + "': " + line, e);
        }
    }

    private static BigDecimal parsePrice(String priceText, String line) {
        if (priceText.isEmpty()) {
            throw new InvalidOrderException("a LIMIT order needs a price: " + line);
        }
        try {
            return new BigDecimal(priceText);
        } catch (NumberFormatException e) {
            throw new InvalidOrderException("bad price '" + priceText + "': " + line, e);
        }
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
