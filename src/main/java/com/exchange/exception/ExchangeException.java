package com.exchange.exception;

/**
 * Base type for every <em>business-rule</em> failure the exchange can raise.
 *
 * <h2>Where this sits in the {@code Throwable} family</h2>
 * <pre>
 *   Throwable
 *   ├─ Error                 (JVM is broken: OutOfMemoryError, StackOverflowError) — don't catch
 *   └─ Exception
 *      ├─ (checked)          IOException, SQLException — "expected, recoverable, caller must handle"
 *      └─ RuntimeException   (unchecked) — programming errors & business-rule violations
 *         └─ ExchangeException   ← we live here
 *            ├─ InvalidOrderException
 *            ├─ InsufficientFundsException
 *            └─ UnknownInstrumentException
 * </pre>
 *
 * <h2>Why <b>unchecked</b> (extends {@link RuntimeException}, not {@link Exception})</h2>
 * <p>The choice between checked and unchecked is the central Stage&nbsp;3 decision,
 * so here is the reasoning written down:
 * <ol>
 *   <li><b>The recovery point is singular.</b> These failures are produced deep
 *       inside the engine ({@code Exchange.submit} → {@code OrderBook.match} →
 *       settlement) but there is exactly one sensible place to handle them: the
 *       interactive {@code ConsoleUI} loop, or the batch {@code SessionRunner}.
 *       Making them checked would force {@code throws} clauses on every method in
 *       that call chain even though none of those methods can do anything useful
 *       about it — that is the "checked-exception pollution" the language is
 *       famous for.</li>
 *   <li><b>They are <em>contract</em> violations, not environmental ones.</b> A
 *       caller can avoid {@link InsufficientFundsException} by checking the
 *       balance first, can avoid {@link UnknownInstrumentException} by listing the
 *       instruments first, etc. The canonical guidance (Effective Java, Item&nbsp;71)
 *       is: use unchecked for conditions the caller could have prevented. Contrast
 *       with {@link java.io.IOException} — the disk filling up mid-write is
 *       genuinely outside the caller's control, so I/O stays <b>checked</b> (see
 *       {@code SessionLoader} / {@code TradeWriter}). That deliberate split is the
 *       point.</li>
 *   <li><b>A common supertype enables one tidy catch.</b> Because every domain
 *       failure extends {@code ExchangeException}, the REPL can write
 *       {@code catch (ExchangeException e)} once and report any of them cleanly,
 *       while still being free to {@code catch} a specific subtype first when it
 *       wants a tailored message.</li>
 * </ol>
 *
 * <p><b>The trade-off we accept:</b> unchecked means the compiler will not remind
 * a future caller that these can be thrown — that knowledge lives in Javadoc and
 * tests instead. We judge that acceptable here because the single, well-known
 * recovery point makes "forgot to handle it" unlikely.
 *
 * <p>Note the two constructors: the {@code (String, Throwable)} form preserves a
 * <em>cause</em>, which is how we do <b>exception translation</b> — catching a
 * low-level {@code NumberFormatException} or {@code IllegalStateException} and
 * rethrowing it as a domain exception <em>without losing the original stack
 * trace</em>. See {@link Throwable#getCause()}.
 */
public class ExchangeException extends RuntimeException {

    public ExchangeException(String message) {
        super(message);
    }

    /**
     * @param message human-readable description of the domain failure
     * @param cause   the lower-level exception being translated (kept for the
     *                stack trace via {@link Throwable#getCause()}); may be null
     */
    public ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
