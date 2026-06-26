package com.exchange.session;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.MarketOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import com.exchange.exception.InvalidOrderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionLoaderTest {

    private final SessionLoader loader = new SessionLoader();

    @Test
    void loadsOrdersAndSkipsCommentsAndBlanks(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("orders.csv");
        Files.writeString(file, """
                # header comment
                ORD-1,LIMIT,SELL,AAPL,100,190.00,MM

                ORD-2,MARKET,BUY,AAPL,120,,ACC1
                """);

        List<Order> orders = loader.load(file);

        assertEquals(2, orders.size(), "comment + blank line must be ignored");

        assertTrue(orders.get(0) instanceof LimitOrder);
        LimitOrder limit = (LimitOrder) orders.get(0);
        assertEquals("ORD-1", limit.getId());
        assertEquals(Side.SELL, limit.getSide());
        assertEquals(0, new BigDecimal("190.00").compareTo(limit.getLimitPrice()));

        assertTrue(orders.get(1) instanceof MarketOrder);
        assertEquals("ACC1", orders.get(1).getAccountId());
    }

    @Test
    void missingAccountColumnIsAllowed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("orders.csv");
        Files.writeString(file, "ORD-1,LIMIT,BUY,AAPL,10,50.00\n");

        Order order = loader.load(file).get(0);

        assertNull(order.getAccountId(), "trailing account column is optional");
    }

    @Test
    void badQuantityIsTranslatedWithCause() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,LIMIT,BUY,AAPL,12x,50.00,ACC1"));

        assertTrue(e.getMessage().contains("12x"));
        assertInstanceOf(NumberFormatException.class, e.getCause(),
                "the low-level parse failure should be chained as the cause");
    }

    @Test
    void limitOrderWithoutPriceIsRejected() {
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,LIMIT,BUY,AAPL,10,,ACC1"));
        assertTrue(e.getMessage().toLowerCase().contains("price"));
    }

    @Test
    void unknownTypeAndSideAreRejected() {
        assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,STOP,BUY,AAPL,10,50.00,ACC1"));
        assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,LIMIT,HODL,AAPL,10,50.00,ACC1"));
    }

    @Test
    void tooFewColumnsIsRejected() {
        assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,LIMIT,BUY"));
    }

    @Test
    void negativeQuantityTripsTheConstructorGuardAndIsTranslated() {
        // Order's constructor throws IllegalArgumentException; the loader translates it.
        InvalidOrderException e = assertThrows(InvalidOrderException.class,
                () -> SessionLoader.parseLine("ORD-1,LIMIT,BUY,AAPL,-5,50.00,ACC1"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    void missingFileThrowsCheckedIOException(@TempDir Path dir) {
        // The I/O boundary is the one place we use a CHECKED exception.
        assertThrows(NoSuchFileException.class, () -> loader.load(dir.resolve("nope.csv")));
    }
}
