# Stage 3 — Files & errors → Exceptions, I/O, JVM structure

Notes that map the build to the concepts. New code lives in:

- `com.exchange.exception` — `ExchangeException` and its three subclasses
- `com.exchange.engine` — `Exchange` (the facade), `PnlReport`, `DemoExchange`
- `com.exchange.session` — `SessionLoader`, `TradeWriter`, `SessionRunner`
- `com.exchange.cli` — `ConsoleUI`
- `data/orders.csv` (tracked input) → `data/trades.csv` (generated, gitignored)

Two new runnable mains:

```bash
# Batch: read data/orders.csv, match, write data/trades.csv
java -cp target/classes com.exchange.session.SessionRunner

# Interactive REPL
java -cp target/classes com.exchange.cli.ConsoleUI
```

## The one big idea

**The exception's _type_ encodes who is supposed to recover from it, and where.**
We deliberately split the model in two:

| Failure kind | Example | Exception | Checked? | Caught where |
|---|---|---|---|---|
| Environmental I/O | file missing / unreadable | `IOException`, `NoSuchFileException` | **checked** | `SessionRunner.main` |
| Business-rule | unknown symbol, no funds, short sell, bad CSV field | `ExchangeException` & subclasses | **unchecked** | the REPL loop / per-order `try` |

That split _is_ the lesson. Everything below hangs off it.

## The exception hierarchy

```
Throwable
├─ Error                 JVM is broken (OutOfMemoryError…) — never catch
└─ Exception
   ├─ (checked)          IOException, SQLException — "expected, recoverable, must handle"
   └─ RuntimeException   (unchecked) — programming errors & business-rule violations
      └─ ExchangeException                ← our base type
         ├─ InvalidOrderException
         ├─ InsufficientFundsException
         └─ UnknownInstrumentException
```

- **`Error`** is for unrecoverable JVM conditions. You don't catch it.
- **`Exception`** splits into **checked** (anything not under `RuntimeException`) and
  **unchecked** (`RuntimeException` and below).
- All three domain exceptions share `ExchangeException`, so one
  `catch (ExchangeException e)` handles any of them — see `ExceptionHierarchyTest`.

## Checked vs unchecked — the decision, written down

**Decision: the domain hierarchy is _unchecked_ (`extends RuntimeException`).**
Reasoning (also in `ExchangeException`'s Javadoc):

1. **Single recovery point.** These failures are produced deep in
   `Exchange.submit → OrderBook.match → settle`, but there is exactly one place
   that can act on them: the REPL loop or the batch runner. Checked exceptions
   would force `throws` clauses up the entire call chain through methods that can
   do nothing about them — the classic "checked-exception pollution".
2. **Caller-preventable = unchecked.** The guidance (Effective Java, Item 71) is:
   use unchecked for conditions the caller could have avoided (check the balance,
   list the instruments first). Use checked for genuinely external conditions.
   The disk filling mid-write is _not_ preventable by the caller → I/O stays
   **checked**. That contrast is intentional.
3. **One tidy catch.** A common supertype lets the boundary write a single
   handler while still special-casing a subtype when it wants a tailored message.

**Trade-off we accept:** the compiler won't remind a future caller these can be
thrown; that knowledge lives in Javadoc + tests instead. Acceptable because the
recovery point is singular and well-known.

## multi-catch

`SessionRunner.run` rejects a bad order without aborting the batch:

```java
} catch (UnknownInstrumentException | InsufficientFundsException | InvalidOrderException e) {
    rejected++;
    System.out.printf("REJECT  %-7s %s%n", order.getId(), e.getMessage());
}
```

One handler, three sibling types, `e` typed as their common supertype. (Here we
_could_ catch `ExchangeException` directly; multi-catch is the right tool when the
siblings don't share a convenient supertype, or when you want to catch some but
not all of them.) Run the demo and `BAD-1/2/3` each trip a different arm.

## finally vs try-with-resources (AutoCloseable)

- **`finally`** runs whether the `try` succeeds or throws — the old way to close
  resources, but verbose and easy to get wrong (close can itself throw and mask
  the original).
- **try-with-resources** declares an `AutoCloseable` in the `try (...)` header and
  closes it automatically, in reverse order, _suppressing_ secondary close-time
  exceptions rather than masking the primary one.

Three resources in this stage are managed this way:

```java
try (Stream<String> lines = Files.lines(path, UTF_8)) { ... }      // SessionLoader
try (BufferedWriter w = Files.newBufferedWriter(path, UTF_8)) { } // TradeWriter
try (Scanner scanner = new Scanner(System.in)) { ui.run(scanner); } // ConsoleUI.main
```

The first is the surprising one: **a `Stream` is `AutoCloseable`.** `Files.lines`
holds an open file handle behind the stream, so it _must_ be closed — and the
try-with-resources does it even if `parseLine` throws partway through the pipeline.

## Exception translation & chaining (cause)

A low-level `NumberFormatException` ("For input string: 12x") means nothing to a
trader. `SessionLoader` catches it and rethrows a domain exception that names the
offending line, **passing the original as the cause** so the stack trace survives:

```java
try { return Integer.parseInt(quantityText); }
catch (NumberFormatException e) {
    throw new InvalidOrderException("bad quantity '" + quantityText + "': " + line, e);
}                                                                            // ^ cause
```

Same pattern in `Exchange.settle`: if a low-level `Account` guard
(`IllegalStateException`) trips, it's translated to `InsufficientFundsException`
with the cause attached. `SessionLoaderTest.badQuantityIsTranslatedWithCause`
asserts `e.getCause()` is the `NumberFormatException`. Never swallow the cause —
`throw new X(msg)` (no `, e`) throws away the breadcrumb trail.

## Cost of exceptions — don't use them for control flow

Throwing fills in a stack trace (`fillInStackTrace`), which is relatively
expensive; an exception per loop iteration is an anti-pattern. So the **hot path
returns values, not exceptions**:

- `OrderBook.match` returns a (possibly empty) `List<Trade>` — an order that
  doesn't cross is a normal empty result, not an exception.
- `bestBid()/bestAsk()` return `Optional`, `TradeLog.lastPrice` returns
  `Optional` — "no data" is a value.

Exceptions here are reserved for genuinely exceptional, rule-breaking input.

## I/O vs NIO basics

- **Old `java.io`**: `InputStream`/`OutputStream` (bytes) vs `Reader`/`Writer`
  (chars, charset-aware). `Scanner` (REPL) wraps a stream and tokenises text.
- **NIO `java.nio.file`**: `Path` (a location) + `Files` (static operations).
  We use `Path.of("data","orders.csv")`, `Files.lines`, `Files.newBufferedWriter`,
  `Files.writeString`/`readAllLines` (in tests).
- **Charset**: always passed explicitly as `StandardCharsets.UTF_8` on both read
  and write. Relying on the platform default is the classic "works on my machine,
  mojibake on the server" bug.
- **Buffering**: `newBufferedWriter` batches small writes into block-sized flushes
  instead of hitting the OS per `write` call.

## String handling

- **Immutability + pool.** `String` is immutable; string literals are interned in
  the **string pool**, so equal literals share one object. Immutability is also
  why `String` is a safe `HashMap` key (Stage 2's registry) — its hashCode can't
  change after insertion.
- **`StringBuilder` in loops.** `ConsoleUI.renderBook` accumulates the depth
  ladder with a `StringBuilder`, not `s += ...`. `+=` in a loop allocates a new
  `String` every iteration → O(n²) garbage; `StringBuilder` mutates one buffer.
  (`TradeWriter` goes one better: it writes each row straight to the buffered
  writer and never builds the whole file in memory.)
- **`String.format`.** Used for aligned columns in `PnlReport.format`,
  `TradeWriter.toCsvRow`, and the REPL's tabular output.
- **Text blocks** (`""" ... """`, Java 15+). The help screen, welcome banner and
  the `PnlReport` template are text blocks — the layout is the literal, no
  `"\n"`-glued concatenation. Tests also use them to author multi-line CSV input.

## The Exchange facade (what glues it together)

`Exchange.submit(order)` is the single entry point: **validate → match → settle**.

- validate: unknown symbol → `UnknownInstrumentException`; unknown account or short
  sell → `InvalidOrderException`; unaffordable buy → `InsufficientFundsException`.
- match: delegates to the Stage 2 `OrderBook`.
- settle: moves cash + positions via the Stage 1 `Account` primitives, translating
  any guard failure into a domain exception.

`pnl(account)` marks open positions to each symbol's last traded price:
`equity = cash + Σ(qty × mark)`, `P&L = equity − initialCash`. Buying at the mark
is P&L-neutral (you swap cash for equal-valued stock); P&L only moves as marks do —
see `ExchangeTest.pnlIsZeroInAFlatMarket` and `pnlRisesWhenTheMarkRises`.

> **Known simplification:** we validate worst-case cost up front but don't reserve
> cash while a limit order rests, so concurrent buys from one account could
> over-commit. If that surfaces in settlement, the `Account` guard trips and we
> translate it rather than corrupt state. A real venue reserves at rest time;
> that's out of scope for a stage about the exception model.

## Checkpoint

`data/orders.csv` runs through `SessionRunner` to **6 accepted, 3 rejected, 4
trades**, and the three rejects exercise all three exception types via one
multi-catch:

```
REJECT  BAD-1   unknown instrument: TSLA                       (UnknownInstrumentException)
REJECT  BAD-2   account ACC1 has insufficient funds: ...       (InsufficientFundsException)
REJECT  BAD-3   account ACC1 cannot sell 50 MSFT — holds 0 ... (InvalidOrderException)
```

And the REPL survives every malformed command (`buy AAPL 100 notanumber`,
unknown commands, unknown accounts) without a stack trace leaking — see
`ConsoleUITest`. ✅
