package com.exchange.book;

import com.exchange.domain.Order;
import com.exchange.domain.OrderStatus;
import com.exchange.repository.InMemoryRepository;
import com.exchange.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Tracks every live order by id so we can cancel in O(1).
 *
 * <p>Backed by the generic {@link InMemoryRepository} keyed on
 * {@code Order::getId} — that is a {@code HashMap} under the hood, so
 * {@link #find(String)} is amortised O(1). The registry adds the order-specific
 * behaviour (cancellation) on top of the generic CRUD.
 *
 * <h2>What if {@code hashCode} is broken?</h2>
 * <p>The whole O(1) promise rests on the key's {@code hashCode}/{@code equals}
 * contract. Here the key is a {@code String}, which is immutable and has a
 * well-distributed {@code hashCode}, so we are safe. But in general:
 * <ul>
 *   <li><b>Constant {@code hashCode} (e.g. {@code return 1;})</b> — still
 *       <em>correct</em>, but every key lands in one bucket, so lookups degrade
 *       to O(n) (a linked list / tree scan).</li>
 *   <li><b>{@code hashCode} inconsistent with {@code equals}</b> — outright
 *       <em>broken</em>: {@code get} hashes to a different bucket than the one
 *       {@code put} used, so the entry is "lost" and lookup returns null even
 *       though an equal key exists.</li>
 *   <li><b>Mutating a field used by {@code hashCode} after insertion</b> — same
 *       failure: the key is now in the wrong bucket.</li>
 * </ul>
 * See {@code HashContractTest} for executable demonstrations of both.
 */
public class OrderRegistry {

    private final Repository<Order, String> repository =
            new InMemoryRepository<>(Order::getId);

    /** Register a newly accepted order. */
    public Order register(Order order) {
        return repository.save(order);
    }

    /** O(1) lookup by id. */
    public Optional<Order> find(String orderId) {
        return repository.findById(orderId);
    }

    public boolean contains(String orderId) {
        return repository.existsById(orderId);
    }

    public List<Order> all() {
        return repository.findAll();
    }

    public long size() {
        return repository.count();
    }

    /**
     * Cancel an order by id: mark it {@code CANCELLED} and drop it from the
     * registry. Already-filled or already-cancelled orders cannot be cancelled.
     *
     * @return the cancelled order, or empty if it was unknown or terminal
     */
    public Optional<Order> cancel(String orderId) {
        Optional<Order> found = repository.findById(orderId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Order order = found.get();
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.CANCELLED) {
            return Optional.empty();
        }
        order.setStatus(OrderStatus.CANCELLED);
        repository.deleteById(orderId);
        return Optional.of(order);
    }
}
