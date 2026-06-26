package com.exchange.book;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.Order;
import com.exchange.domain.OrderStatus;
import com.exchange.domain.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderRegistryTest {

    private static Order order(String id) {
        return new LimitOrder(id, Side.BUY, "AAPL", 10, new BigDecimal("100"));
    }

    @Test
    void registerThenFind() {
        OrderRegistry registry = new OrderRegistry();
        registry.register(order("ORD-1"));
        assertTrue(registry.find("ORD-1").isPresent());
        assertTrue(registry.contains("ORD-1"));
        assertEquals(1L, registry.size());
    }

    @Test
    void cancelMarksCancelledAndRemoves() {
        OrderRegistry registry = new OrderRegistry();
        Order o = registry.register(order("ORD-1"));

        Order cancelled = registry.cancel("ORD-1").orElseThrow();
        assertSame(o, cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertFalse(registry.contains("ORD-1"), "cancelled order leaves the registry");
        assertEquals(0L, registry.size());
    }

    @Test
    void cancelUnknownIsEmpty() {
        assertTrue(new OrderRegistry().cancel("ghost").isEmpty());
    }

    @Test
    void cannotCancelAlreadyFilledOrder() {
        OrderRegistry registry = new OrderRegistry();
        Order o = registry.register(order("ORD-1"));
        o.fill(o.getQuantity());                       // now FILLED
        assertTrue(registry.cancel("ORD-1").isEmpty());
        assertEquals(OrderStatus.FILLED, o.getStatus());
    }
}
