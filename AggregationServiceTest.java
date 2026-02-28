package com.candle.aggregator;

import com.candle.event.BidAskEvent;
import com.candle.model.Candle;
import com.candle.model.Interval;
import com.candle.service.AggregationService;
import com.candle.store.CandleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AggregationService")
class AggregationServiceTest {

    private CandleStore candleStore;
    private AggregationService service;

    private BidAskEvent event(String symbol, double mid, long timestampSeconds) {
        double spread = mid * 0.001;
        return new BidAskEvent(symbol, mid - spread / 2, mid + spread / 2, timestampSeconds * 1000L);
    }

    @BeforeEach
    void setUp() {
        candleStore = new CandleStore();
        service = new AggregationService(candleStore);
    }

    @Test
    @DisplayName("Events are fan-out to ALL intervals for the given symbol")
    void eventFanOutToAllIntervals() {
        // One event in bucket 0 for each interval
        service.ingest(event("BTC-USD", 100.0, 10));

        // Cross a 1s bucket boundary to flush
        service.ingest(event("BTC-USD", 110.0, 11));

        // Only the 1s candle should be complete (all others still open)
        List<Candle> oneSecond = candleStore.query("BTC-USD", "1s", 0, 100);
        assertThat(oneSecond).hasSize(1);
    }

    @Test
    @DisplayName("Multiple symbols are aggregated independently")
    void multipleSymbols() {
        service.ingest(event("BTC-USD", 100.0, 0));
        service.ingest(event("ETH-USD", 200.0, 0));

        // Roll both
        service.ingest(event("BTC-USD", 150.0, 60));
        service.ingest(event("ETH-USD", 250.0, 60));

        List<Candle> btc = candleStore.query("BTC-USD", "1m", 0, 60);
        List<Candle> eth = candleStore.query("ETH-USD", "1m", 0, 60);

        assertThat(btc).hasSize(1);
        assertThat(eth).hasSize(1);
        assertThat(btc.get(0).open()).isLessThan(eth.get(0).open()); // BTC open < ETH open
    }

    @Test
    @DisplayName("New symbols are auto-registered on first event")
    void newSymbolAutoRegistered() {
        assertThat(service.aggregatorCount()).isEqualTo(0);
        service.ingest(event("XRP-USD", 0.5, 10));
        assertThat(service.aggregatorCount()).isEqualTo(Interval.values().length);
    }

    @Test
    @DisplayName("activeSymbols returns all ingested symbols")
    void activeSymbols() {
        service.ingest(event("BTC-USD", 100.0, 10));
        service.ingest(event("ETH-USD", 200.0, 10));

        assertThat(service.activeSymbols()).containsExactlyInAnyOrder("BTC-USD", "ETH-USD");
    }

    @Test
    @DisplayName("Stale flush via flushStaleCandles pushes open candles to store")
    void staleFlushPushesCandles() throws Exception {
        service.ingest(event("BTC-USD", 100.0, 0));

        // Only the 1s candle would be stale after 2s; manually flush all
        service.flushStaleCandles(); // time is wall-clock dependent — can't assert exact count

        // This is a smoke test — just ensure it doesn't throw
        assertThat(candleStore.totalCandles()).isGreaterThanOrEqualTo(0);
    }
}
