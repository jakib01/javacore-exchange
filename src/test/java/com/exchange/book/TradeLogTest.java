package com.exchange.book;

import com.exchange.domain.Trade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TradeLogTest {

    private static Trade trade(String symbol, String price, int qty, String buyAcct, String sellAcct) {
        return new Trade("B-" + buyAcct, "S-" + sellAcct, buyAcct, sellAcct,
                symbol, new BigDecimal(price), qty, Instant.now());
    }

    private TradeLog populated() {
        TradeLog log = new TradeLog();
        // AAPL: 100@50 and 50@60  -> volume 150, VWAP = (5000 + 3000) / 150 = 53.3333...
        log.record(trade("AAPL", "50", 100, "ACC-A", "ACC-B"));
        log.record(trade("AAPL", "60", 50, "ACC-A", "ACC-C"));
        // TSLA: 10@200 -> volume 10, VWAP = 200
        log.record(trade("TSLA", "200", 10, "ACC-B", "ACC-A"));
        return log;
    }

    @Test
    void totalVolumePerSymbol() {
        Map<String, Long> volume = populated().totalVolumePerSymbol();
        assertEquals(150L, volume.get("AAPL"));
        assertEquals(10L, volume.get("TSLA"));
    }

    @Test
    void vwapForSymbol() {
        Optional<BigDecimal> aapl = populated().vwap("AAPL");
        assertTrue(aapl.isPresent());
        // 8000 / 150 = 53.33333333
        assertEquals(0, new BigDecimal("53.33333333").compareTo(aapl.get()));
    }

    @Test
    void vwapEmptyForUnknownSymbol() {
        assertTrue(populated().vwap("NFLX").isEmpty());
    }

    @Test
    void vwapPerSymbolMatchesSingleSymbolVwap() {
        TradeLog log = populated();
        Map<String, BigDecimal> all = log.vwapPerSymbol();
        assertEquals(0, all.get("AAPL").compareTo(log.vwap("AAPL").orElseThrow()));
        assertEquals(0, new BigDecimal("200").compareTo(all.get("TSLA")));
    }

    @Test
    void topNActiveAccounts_countsBothSides_andRanks() {
        // ACC-A appears in all 3 trades; ACC-B in 2; ACC-C in 1.
        List<Map.Entry<String, Long>> top = populated().topNActiveAccounts(2);

        assertEquals(2, top.size());
        assertEquals("ACC-A", top.get(0).getKey());
        assertEquals(3L, top.get(0).getValue());
        assertEquals("ACC-B", top.get(1).getKey());
        assertEquals(2L, top.get(1).getValue());
    }

    @Test
    void topNActiveAccounts_ignoresNullAccounts() {
        TradeLog log = new TradeLog();
        // Stage-1 style trade with no account attribution.
        log.record(new Trade("B1", "S1", "AAPL", new BigDecimal("10"), 5, Instant.now()));
        assertTrue(log.topNActiveAccounts(5).isEmpty());
    }

    @Test
    void topNActiveAccounts_nonPositiveNReturnsEmpty() {
        assertTrue(populated().topNActiveAccounts(0).isEmpty());
    }

    @Test
    void getTradesIsUnmodifiable() {
        TradeLog log = populated();
        assertThrows(UnsupportedOperationException.class, () -> log.getTrades().clear());
    }
}
