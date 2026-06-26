# Stage 2 — Collections & Generics

Notes that map the build to the concepts. The code lives in:

- `com.exchange.book` — `OrderBook`, `OrderRegistry`, `TradeLog`, `OrderBookDemo`
- `com.exchange.repository` — `Repository<T,ID>`, `InMemoryRepository<T,ID>`
- `com.exchange.concepts` (tests) — `HashContractTest`, `ConcurrentModificationTest`

## The one big idea

**Price-time priority is not coded; it emerges from the data structures.**

| Requirement | Structure | Why |
|---|---|---|
| Best price first | `TreeMap` keyed by price | red-black tree → `firstEntry()` is the best level in O(log n) |
| Bids: highest first | `TreeMap<>(Comparator.reverseOrder())` | reverse natural order |
| Asks: lowest first | `TreeMap<>()` | natural order |
| Time priority at a price | `ArrayDeque` (FIFO) | new orders `addLast`, matches `pollFirst` |

The matching loop in `OrderBook.match` never sorts — it just reads `firstEntry()`
and `peekFirst()`. That *is* price-time priority.

## Collection hierarchy & big-O

- **List** — ordered, indexed, duplicates ok.
  - `ArrayList`: O(1) get/append, O(n) middle insert/remove. Default choice.
  - `LinkedList`: O(1) head/tail ops, O(n) get(i). Rarely worth it; use `ArrayDeque` for queue/stack.
- **Set** — no duplicates. `HashSet` O(1), `LinkedHashSet` O(1)+insertion order, `TreeSet` O(log n)+sorted.
- **Queue/Deque** — `ArrayDeque` is the go-to FIFO/LIFO (faster than `LinkedList`, no nulls).
- **Map** — `HashMap` O(1) unordered, `LinkedHashMap` O(1)+insertion order, `TreeMap` O(log n)+sorted.

## HashMap internals (the interview question)

- Array of **buckets**; index = `(n-1) & spread(hash)` where `spread` mixes high bits down.
- Collisions chain in a **linked list**; once a bucket reaches **8** entries (and table ≥ 64) it
  **treeifies** into a red-black tree (O(log n) instead of O(n) for that bucket).
- **Load factor** 0.75: when `size > capacity * 0.75` the table **resizes** (doubles) and rehashes.
- **Lookup cost depends entirely on `hashCode`.** See `HashContractTest`:
  - equal keys with different hashCodes → entry is **unreachable** (wrong bucket).
  - mutating a hashCode field after insert → entry is **stranded**.
  - constant hashCode → correct but O(n) (everything in one bucket).
  - `String`/`UUID` are immutable with good hashCodes → safe registry keys.

## TreeMap = red-black tree

Navigation methods we get for free: `firstEntry()/lastEntry()`, `firstKey()`,
`ceilingKey()/floorKey()`, `headMap()/tailMap()/subMap()`, `pollFirstEntry()`.

**`BigDecimal` gotcha:** `TreeMap` decides key identity with `compareTo`, **not** `equals`.
`new BigDecimal("50")` and `new BigDecimal("50.00")` are `compareTo`-equal (equals-unequal due to
scale), so they collapse onto one price level — exactly what we want. (This is also why tests assert
prices with `compareTo` == 0, not `equals`.)

## PriorityQueue vs TreeMap for a book

A `PriorityQueue` gives you the best element in O(1) peek / O(log n) poll, but:

- it orders **individual orders**, so there's no natural "price level" grouping;
- **no FIFO within equal priority** — a heap makes no time-priority guarantee among equal-price
  orders, which breaks the whole point;
- removing an arbitrary order (cancel) is O(n).

`TreeMap<price, ArrayDeque<order>>` keeps price levels explicit and time priority exact. That's why
the book uses it. (A `PriorityQueue` is great for *event* scheduling, not for a limit book.)

## Comparable vs Comparator

- `Comparable<T>` — natural order, `int compareTo(T)` on the type itself (e.g. `BigDecimal`).
- `Comparator<T>` — external/alternate order. Compose with
  `Comparator.comparing(...).thenComparing(...)` and `.reversed()`.
- In `TradeLog.topNActiveAccounts`: `Map.Entry.comparingByValue().reversed().thenComparing(comparingByKey())`
  → busiest first, ties broken by id (stable, deterministic).

## Generics

- `Repository<T, ID>` — two type parameters; the compiler enforces `findById` returns `Optional<T>`.
- `InMemoryRepository<T, ID>` takes a `Function<T, ID>` id-extractor, so `save(entity)` needs no
  separate key. Same class instantiates as `<Order,String>` and `<String,Integer>` (see test).
- **Bounded types**: `<T extends Comparable<T>>` when the type must be orderable.
- **Wildcards / PECS** — *Producer Extends, Consumer Super*:
  - `List<? extends Number>` — you read Numbers out (producer); can't add.
  - `List<? super Integer>` — you put Integers in (consumer); reads come out as Object.
- **Type erasure**: generics are compile-time only; at runtime `List<String>` is just `List`.
  Consequence: `new T[]` is illegal (no runtime type to instantiate) — use
  `(T[]) new Object[n]` or, better, a `List<T>`.

## Streams deep-dive (`TradeLog`)

- `totalVolumePerSymbol` → `groupingBy(Trade::getSymbol, summingLong(Trade::getQuantity))`.
- `vwapPerSymbol` → `groupingBy(symbol, teeing(reducing(notional), summingLong(qty), divide))` —
  two downstream collectors in one pass.
- `topNActiveAccounts` → `flatMap` both account ids per trade, `filter` nulls,
  `groupingBy(id, counting())`, then `sorted(...).limit(n)`.
- **Lazy**: intermediate ops (`map/filter/flatMap/sorted`) do nothing until a terminal op
  (`collect/reduce/sum/forEach`) runs.
- **Stream vs loop**: streams win for declarative transforms/aggregations; a plain loop is clearer
  (and faster) for simple mutation or early exit. The book's hot matching loop is deliberately a
  `while` loop, not a stream.

## Optional

`OrderBook.bestBid()/bestAsk()` return `Optional<BigDecimal>` — an empty book has no best price,
and `Optional` makes the caller handle that instead of risking a null. Use `isEmpty()`,
`orElseThrow()`, `map(...)`.

## fail-fast iterators & ConcurrentModificationException

See `ConcurrentModificationTest`: removing from a list inside a for-each throws CME (fail-fast
`modCount` check). Fixes: `Iterator.remove()` or `Collection.removeIf(...)`. `OrderBook.removeFromSide`
uses `removeIf` for exactly this reason.

## Checkpoint

`OrderBookTest.checkpoint_marketBuyWalksAsksPriceThenTime`:
SELL 100@50, SELL 50@49, BUY 120 market → trades **50@49** then **70@50**, remaining ask **30@50**. ✅
