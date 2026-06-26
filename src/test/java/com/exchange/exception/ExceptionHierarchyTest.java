package com.exchange.exception;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins down the two design decisions of the Stage 3 exception model:
 * the hierarchy shape, and the unchecked + chaining behaviour.
 */
class ExceptionHierarchyTest {

    @Test
    void allDomainExceptionsShareTheExchangeExceptionSupertype() {
        assertTrue(ExchangeException.class.isAssignableFrom(InvalidOrderException.class));
        assertTrue(ExchangeException.class.isAssignableFrom(InsufficientFundsException.class));
        assertTrue(ExchangeException.class.isAssignableFrom(UnknownInstrumentException.class));
    }

    @Test
    void theyAreUnchecked() {
        // The whole point of the "unchecked" decision: these are RuntimeExceptions,
        // so no method is forced to declare or catch them.
        assertTrue(RuntimeException.class.isAssignableFrom(ExchangeException.class));
        assertFalse(isCheckedOnly(ExchangeException.class));
    }

    @Test
    void oneCatchOfTheSupertypeHandlesEverySubtype() {
        // This is exactly what ConsoleUI / SessionRunner rely on.
        for (ExchangeException e : new ExchangeException[]{
                new InvalidOrderException("bad"),
                new InsufficientFundsException("ACC1", new BigDecimal("10"), new BigDecimal("5")),
                new UnknownInstrumentException("ZZZZ")}) {
            assertThrows(ExchangeException.class, () -> {
                throw e;
            });
        }
    }

    @Test
    void translationKeepsTheOriginalCause() {
        NumberFormatException root = new NumberFormatException("For input string: \"12x\"");
        InvalidOrderException translated = new InvalidOrderException("bad quantity: line 4", root);

        assertSame(root, translated.getCause(), "the cause must be preserved for the stack trace");
        assertTrue(translated.getMessage().contains("line 4"));
    }

    @Test
    void insufficientFundsCarriesTheNumbers() {
        InsufficientFundsException e =
                new InsufficientFundsException("ACC1", new BigDecimal("200"), new BigDecimal("50"));

        assertEquals("ACC1", e.getAccountId());
        assertEquals(new BigDecimal("200"), e.getRequired());
        assertEquals(new BigDecimal("50"), e.getAvailable());
    }

    @Test
    void unknownInstrumentCarriesTheSymbol() {
        assertEquals("ZZZZ", new UnknownInstrumentException("ZZZZ").getSymbol());
    }

    private static boolean isCheckedOnly(Class<?> type) {
        return Exception.class.isAssignableFrom(type)
                && !RuntimeException.class.isAssignableFrom(type);
    }
}
