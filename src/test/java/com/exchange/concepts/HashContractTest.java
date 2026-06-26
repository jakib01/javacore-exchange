package com.exchange.concepts;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable answer to "what happens to lookup if hashCode is broken?" — the
 * discussion the Stage 2 brief asks for around {@code OrderRegistry}.
 *
 * <p>The hashCode/equals contract has two clauses that matter here:
 * <ol>
 *   <li>Equal objects MUST have equal hash codes. Break this and {@code get}
 *       searches the wrong bucket — the entry is unreachable.</li>
 *   <li>An object's hash code must not change while it is a key. Break this and
 *       the entry is stranded in its original bucket.</li>
 * </ol>
 * A constant hashCode honours the contract but destroys performance.
 */
class HashContractTest {

    /** equals() is value-based, but hashCode() differs per instance — BROKEN. */
    static final class BrokenKey {
        private static int counter = 0;
        final int id;
        final int tag;                       // unique per instance, drives a deterministic-but-wrong hash

        BrokenKey(int id) {
            this.id = id;
            this.tag = counter++;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BrokenKey && ((BrokenKey) o).id == id;
        }

        @Override
        public int hashCode() {
            return tag;                      // two equal keys -> different hash codes
        }
    }

    /** hashCode() reads a mutable field. */
    static final class MutableKey {
        int id;

        MutableKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MutableKey && ((MutableKey) o).id == id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    @Test
    void stringAndUuidKeysAreSafe() {
        Map<UUID, String> map = new HashMap<>();
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        map.put(id, "order");
        // A freshly parsed-but-equal UUID still finds the entry: immutable + good hashCode.
        assertEquals("order", map.get(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void brokenHashCode_makesEqualKeyUnreachable() {
        Map<BrokenKey, String> map = new HashMap<>();
        BrokenKey inserted = new BrokenKey(1);
        map.put(inserted, "lost");

        BrokenKey equalKey = new BrokenKey(1);
        assertEquals(inserted, equalKey, "equals() says they are the same");
        assertNotEquals(inserted.hashCode(), equalKey.hashCode(), "but their hashCodes differ");

        assertNull(map.get(equalKey), "different hash -> wrong bucket -> not found");
        assertEquals("lost", map.get(inserted), "the original key object still resolves");
    }

    @Test
    void mutatingKeyAfterInsert_strandsTheEntry() {
        Map<MutableKey, String> map = new HashMap<>();
        MutableKey key = new MutableKey(1);
        map.put(key, "value");

        key.id = 2;                              // hashCode changes after insertion
        assertNull(map.get(key), "entry is stranded under its old bucket");
        assertNull(map.get(new MutableKey(2)), "and unreachable by the new value too");
    }

    @Test
    void constantHashCode_isCorrectButDegradesToLinearScan() {
        // hashCode() == 1 for all keys: still CORRECT (equal keys share a hash),
        // but every entry collides into one bucket, so lookups are O(n).
        final class ConstKey {
            final int id;

            ConstKey(int id) {
                this.id = id;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof ConstKey && ((ConstKey) o).id == id;
            }

            @Override
            public int hashCode() {
                return 1;
            }
        }

        Map<ConstKey, Integer> map = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(new ConstKey(i), i);
        }
        assertEquals(100, map.size());
        assertEquals(42, map.get(new ConstKey(42)));   // correctness survives the bad distribution
    }
}
