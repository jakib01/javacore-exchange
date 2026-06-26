package com.exchange.exception;

import java.math.BigDecimal;

/**
 * Thrown when an account cannot afford an order or a fill: the cash required
 * exceeds the cash available (balance minus what is already reserved).
 *
 * <p>Carries the numbers ({@code required} vs {@code available}) as fields, not
 * just baked into the message string, so a programmatic caller — or a test — can
 * assert on them without parsing text.
 */
public class InsufficientFundsException extends ExchangeException {

    private final String accountId;
    private final BigDecimal required;
    private final BigDecimal available;

    public InsufficientFundsException(String accountId, BigDecimal required, BigDecimal available) {
        super("account " + accountId + " has insufficient funds: required " + required
                + ", available " + available);
        this.accountId = accountId;
        this.required = required;
        this.available = available;
    }

    /** Translation form: wrap a lower-level guard failure (keeps its stack via cause). */
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
        this.accountId = null;
        this.required = null;
        this.available = null;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getRequired() {
        return required;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
