package com.exchange.repository;

import java.util.List;
import java.util.Optional;

/**
 * A minimal generic repository abstraction.
 *
 * <p>Two type parameters:
 * <ul>
 *   <li>{@code T}  — the entity type being stored (e.g. {@code Order}).</li>
 *   <li>{@code ID} — the type of that entity's identifier (e.g. {@code String}).</li>
 * </ul>
 *
 * <p>This is the "generics somewhere real" piece of Stage 2: the same interface
 * works for any entity/key pair without casting or {@code Object}, and the
 * compiler enforces that {@link #findById(Object)} returns the right entity type.
 *
 * <p>{@link #findById(Object)} returns {@link Optional} rather than a nullable
 * reference so callers are forced to deal with the "not found" case explicitly.
 */
public interface Repository<T, ID> {

    /** Insert or replace the entity, returning it for fluent chaining. */
    T save(T entity);

    /** Look up by id; empty if no entity is stored under that id. */
    Optional<T> findById(ID id);

    /** A snapshot copy of every stored entity. */
    List<T> findAll();

    /** @return {@code true} if an entity existed under {@code id} and was removed. */
    boolean deleteById(ID id);

    boolean existsById(ID id);

    long count();
}
