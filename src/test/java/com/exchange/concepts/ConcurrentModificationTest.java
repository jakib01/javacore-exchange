package com.exchange.concepts;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Triggering {@link ConcurrentModificationException} on purpose, then the two
 * standard fixes — the fail-fast iterator lesson from the Stage 2 brief.
 *
 * <p>Collection iterators are <em>fail-fast</em>: they snapshot a {@code modCount}
 * and throw if the collection is structurally modified through any path other
 * than the iterator itself while iterating.
 */
class ConcurrentModificationTest {

    private static List<Integer> oneToFive() {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            list.add(i);
        }
        return list;
    }

    @Test
    void removingDuringForEach_throwsConcurrentModificationException() {
        List<Integer> list = oneToFive();
        assertThrows(ConcurrentModificationException.class, () -> {
            for (Integer n : list) {            // enhanced-for hides a fail-fast Iterator
                if (n % 2 == 0) {
                    list.remove(n);             // structural change behind the iterator's back
                }
            }
        });
    }

    @Test
    void iteratorRemove_isSafe() {
        List<Integer> list = oneToFive();
        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 0) {
                it.remove();                    // remove THROUGH the iterator: modCount stays in sync
            }
        }
        assertEquals(List.of(1, 3, 5), list);
    }

    @Test
    void removeIf_isSafeAndConcise() {
        List<Integer> list = oneToFive();
        list.removeIf(n -> n % 2 == 0);         // the modern one-liner
        assertEquals(List.of(1, 3, 5), list);
    }
}
