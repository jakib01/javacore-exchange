package com.exchange.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link HashMap}-style in-memory {@link Repository}.
 *
 * <p>Backed by a {@link LinkedHashMap} so {@link #findAll()} returns entities in
 * insertion order — a {@code HashMap} would give an arbitrary, version-dependent
 * order, which makes tests and demos flaky.
 *
 * <p>The key is derived from each entity by an injected {@link Function}
 * ({@code idExtractor}). That keeps the repository decoupled from how any
 * particular entity exposes its id: pass {@code Order::getId},
 * {@code Account::getId}, etc. {@code save(entity)} therefore needs no separate
 * id argument — it asks the extractor.
 *
 * @param <T>  entity type
 * @param <ID> identifier type
 */
public class InMemoryRepository<T, ID> implements Repository<T, ID> {

    private final Map<ID, T> store = new LinkedHashMap<>();
    private final Function<T, ID> idExtractor;

    public InMemoryRepository(Function<T, ID> idExtractor) {
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor required");
    }

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity required");
        ID id = idExtractor.apply(entity);
        Objects.requireNonNull(id, "extracted id must not be null");
        store.put(id, entity);
        return entity;
    }

    @Override
    public Optional<T> findById(ID id) {
        // O(1) average-case lookup — this is the whole point of a hash-based map.
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean deleteById(ID id) {
        return store.remove(id) != null;
    }

    @Override
    public boolean existsById(ID id) {
        return store.containsKey(id);
    }

    @Override
    public long count() {
        return store.size();
    }
}
