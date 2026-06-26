package com.exchange.exception;

/**
 * Thrown when an order or query references a symbol the exchange does not list.
 * The unknown symbol is kept as a field so callers can, for example, suggest the
 * known instruments instead.
 */
public class UnknownInstrumentException extends ExchangeException {

    private final String symbol;

    public UnknownInstrumentException(String symbol) {
        super("unknown instrument: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
