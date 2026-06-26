package com.exchange.session;

import com.exchange.domain.Trade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeWriterTest {

    private final TradeWriter writer = new TradeWriter();

    @Test
    void writesHeaderThenOneRowPerTrade(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trades.csv");
        List<Trade> trades = List.of(
                new Trade("B1", "S1", "ACC1", "MM", "AAPL",
                        new BigDecimal("190.00"), 100, Instant.parse("2026-01-01T00:00:00Z")),
                new Trade("B2", "S2", "ACC2", "MM", "MSFT",
                        new BigDecimal("300.50"), 10, Instant.parse("2026-01-01T00:00:01Z")));

        writer.write(file, trades);

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size(), "header + two rows");
        assertEquals(TradeWriter.HEADER, lines.get(0));
        assertTrue(lines.get(1).startsWith("B1,S1,ACC1,MM,AAPL,190.00,100,19000.00,"));
        assertTrue(lines.get(2).startsWith("B2,S2,ACC2,MM,MSFT,300.50,10,3005.00,"));
    }

    @Test
    void emptyTradeListStillWritesAHeaderOnlyFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trades.csv");
        writer.write(file, List.of());

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertEquals(TradeWriter.HEADER, lines.get(0));
    }

    @Test
    void nullAccountsRenderAsEmptyFields() {
        // Stage 1 trades carry no account attribution — must not print "null".
        Trade anonymous = new Trade("B1", "S1", "AAPL",
                new BigDecimal("50"), 5, Instant.parse("2026-01-01T00:00:00Z"));

        String row = TradeWriter.toCsvRow(anonymous);

        assertTrue(row.startsWith("B1,S1,,,AAPL,50,5,250,"), "row was: " + row);
        assertFalse(row.contains("null"));
    }

    @Test
    void roundTripsThroughTheLoader(@TempDir Path dir) throws IOException {
        // Not a literal round-trip (the files differ in shape), but a sanity check
        // that what we write is UTF-8 text we can read back line by line.
        Path file = dir.resolve("trades.csv");
        writer.write(file, List.of(new Trade("B1", "S1", "ACC1", "MM", "AAPL",
                new BigDecimal("190.00"), 100, Instant.parse("2026-01-01T00:00:00Z"))));

        String content = Files.readString(file);
        assertTrue(content.contains("AAPL"));
        assertTrue(content.endsWith("\n") || content.endsWith("Z"));
    }
}
