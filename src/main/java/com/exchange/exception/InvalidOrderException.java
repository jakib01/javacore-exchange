package com.exchange.exception;

/**
 * Thrown when an order is malformed or fails validation: non-positive quantity,
 * a missing/zero limit price on a limit order, an unparseable CSV line, an
 * unknown command argument, and so on.
 *
 * <p>This is the natural target for <b>exception translation</b>: a low-level
 * {@link NumberFormatException} from parsing "{@code 12x}" as a quantity carries
 * no business meaning, so {@code SessionLoader} catches it and rethrows an
 * {@code InvalidOrderException} that says <em>which</em> line and field were bad,
 * passing the original as the {@code cause}.
 */
public class InvalidOrderException extends ExchangeException {

    public InvalidOrderException(String message) {
        super(message);
    }

    public InvalidOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
