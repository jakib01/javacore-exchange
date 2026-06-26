package com.exchange.repository;

import com.exchange.domain.LimitOrder;
import com.exchange.domain.Order;
import com.exchange.domain.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRepositoryTest {

    private Repository<Order, String> newOrderRepo() {
        return new InMemoryRepository<>(Order::getId);
    }

    private static Order order(String id) {
        return new LimitOrder(id, Side.BUY, "AAPL", 10, new BigDecimal("100"));
    }

    @Test
    void saveThenFindById() {
        Repository<Order, String> repo = newOrderRepo();
        repo.save(order("ORD-1"));
        Optional<Order> found = repo.findById("ORD-1");
        assertTrue(found.isPresent());
        assertEquals("ORD-1", found.get().getId());
    }

    @Test
    void findByUnknownIdIsEmpty() {
        assertTrue(newOrderRepo().findById("nope").isEmpty());
    }

    @Test
    void saveReplacesSameId() {
        Repository<Order, String> repo = newOrderRepo();
        repo.save(order("ORD-1"));
        repo.save(order("ORD-1"));
        assertEquals(1L, repo.count());
    }

    @Test
    void deleteById() {
        Repository<Order, String> repo = newOrderRepo();
        repo.save(order("ORD-1"));
        assertTrue(repo.deleteById("ORD-1"));
        assertFalse(repo.deleteById("ORD-1"));
        assertFalse(repo.existsById("ORD-1"));
    }

    @Test
    void findAllPreservesInsertionOrder() {
        Repository<Order, String> repo = newOrderRepo();
        repo.save(order("ORD-3"));
        repo.save(order("ORD-1"));
        repo.save(order("ORD-2"));
        List<Order> all = repo.findAll();
        assertEquals(List.of("ORD-3", "ORD-1", "ORD-2"),
                all.stream().map(Order::getId).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void worksForADifferentEntityAndKeyType() {
        // The same generic repo, instantiated with <String, Integer>: proof the
        // generics are not specialised to Order.
        Repository<String, Integer> byLength = new InMemoryRepository<>(String::length);
        byLength.save("abc");
        byLength.save("de");
        assertEquals("abc", byLength.findById(3).orElseThrow());
        assertEquals("de", byLength.findById(2).orElseThrow());
        assertTrue(byLength.findById(99).isEmpty());
    }
}
