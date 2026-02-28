package com.candle.aggregator;

import com.candle.event.BidAskEvent;
import com.candle.model.Candle;
import com.candle.model.Interval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link CandleAggregator}.
 * These tests are the most important — they verify the correctness of OHLC logic.
 */
class CandleAggregatorTest {

    private static final String SYMBOL = "BTC-USD";
    private static final Interval INTERVAL = Interval.ONE_MINUTE; // 60s buckets

    private List<Candle> completedCandles;
    private CandleAggregator aggregator;

    @BeforeEach
    void setUp() {
        completedCandles = new ArrayList<>();
        aggregator = new CandleAggregator(SYMBOL, INTERVAL,
                (interval, candle) -> completedCandles.add(candle));
    }

    // Helper: create a BidAskEvent with a specific mid-price and timestamp (in seconds)
    private BidAskEvent event(double midPrice, long timestampSeconds) {
        double spread = midPrice * 0.001;
        double bid = midPrice - spread / 2;
        double ask = midPrice + spread / 2;
        return new BidAskEvent(SYMBOL, bid, ask, timestampSeconds * 1000L);
    }

    @Nested
    @DisplayName("Single candle formation")
    class SingleCandle {

        @Test
        @DisplayName("First event sets open, high, low, close all to mid-price")
        void firstEventInitializesCandle() {
            aggregator.process(event(100.0, 0));

            // No candle completed yet — still open
            assertThat(completedCandles).isEmpty();

            // Force-flush to inspect state
            Optional<Candle> candle = aggregator.forceFlush();
            assertThat(candle).isPresent();
            Candle c = candle.get();
            assertThat(c.open()).isCloseTo(100.0, within(0.5));
            assertThat(c.high()).isCloseTo(100.0, within(0.5));
            assertThat(c.low()).isCloseTo(100.0, within(0.5));
            assertThat(c.close()).isCloseTo(100.0, within(0.5));
            assertThat(c.volume()).isEqualTo(1);
        }

        @Test
        @DisplayName("OHLC is computed correctly from a sequence of prices")
        void ohlcCorrectlyComputed() {
            // prices: 100, 120, 90, 110 — all within same 60s bucket (t=0..59)
            aggregator.process(event(100.0, 10));  // open = 100
            aggregator.process(event(120.0, 20));  // new high
            aggregator.process(event(90.0,  30));  // new low
            aggregator.process(event(110.0, 40));  // close = 110

            Optional<Candle> candle = aggregator.forceFlush();
            assertThat(candle).isPresent();
            Candle c = candle.get();

            assertThat(c.open()).isCloseTo(100.0, within(0.5));
            assertThat(c.high()).isCloseTo(120.0, within(0.5));
            assertThat(c.low()).isCloseTo(90.0, within(0.5));
            assertThat(c.close()).isCloseTo(110.0, within(0.5));
            assertThat(c.volume()).isEqualTo(4);
        }

        @Test
        @DisplayName("Volume counts all ticks in the bucket")
        void volumeCountsTicks() {
            for (int i = 0; i < 15; i++) {
                aggregator.process(event(100.0 + i, i));
            }
            Optional<Candle> candle = aggregator.forceFlush();
            assertThat(candle.get().volume()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("Candle rollover (bucket crossing)")
    class CandleRollover {

        @Test
        @DisplayName("Event in next bucket completes the previous candle")
        void eventInNewBucketFlushesOld() {
            // Bucket 0 = seconds 0–59
            aggregator.process(event(100.0, 10));
            aggregator.process(event(105.0, 50));

            // Bucket 1 = seconds 60–119 — this should flush bucket 0
            aggregator.process(event(200.0, 60));

            assertThat(completedCandles).hasSize(1);
            Candle flushed = completedCandles.get(0);
            assertThat(flushed.time()).isEqualTo(0L);        // bucket 0 start
            assertThat(flushed.open()).isCloseTo(100.0, within(0.5));
            assertThat(flushed.close()).isCloseTo(105.0, within(0.5));
            assertThat(flushed.volume()).isEqualTo(2);
        }

        @Test
        @DisplayName("Multiple bucket rolls produce multiple completed candles")
        void multipleRolls() {
            aggregator.process(event(100.0, 0));   // bucket 0
            aggregator.process(event(200.0, 60));  // bucket 1 — flushes 0
            aggregator.process(event(300.0, 120)); // bucket 2 — flushes 1
            aggregator.process(event(400.0, 180)); // bucket 3 — flushes 2

            assertThat(completedCandles).hasSize(3);
            assertThat(completedCandles.get(0).time()).isEqualTo(0L);
            assertThat(completedCandles.get(1).time()).isEqualTo(60L);
            assertThat(completedCandles.get(2).time()).isEqualTo(120L);
        }

        @Test
        @DisplayName("Each completed candle carries correct OHLC independently")
        void eachCandleIsIndependent() {
            // Bucket 0: 100 → 150
            aggregator.process(event(100.0, 5));
            aggregator.process(event(150.0, 55));

            // Bucket 1: 200 → 180
            aggregator.process(event(200.0, 65));
            aggregator.process(event(180.0, 115));

            // Trigger flush of bucket 1
            aggregator.process(event(300.0, 125));

            assertThat(completedCandles).hasSize(2);

            Candle c0 = completedCandles.get(0);
            assertThat(c0.open()).isCloseTo(100.0, within(0.5));
            assertThat(c0.close()).isCloseTo(150.0, within(0.5));
            assertThat(c0.high()).isCloseTo(150.0, within(0.5));
            assertThat(c0.low()).isCloseTo(100.0, within(0.5));

            Candle c1 = completedCandles.get(1);
            assertThat(c1.open()).isCloseTo(200.0, within(0.5));
            assertThat(c1.close()).isCloseTo(180.0, within(0.5));
        }
    }

    @Nested
    @DisplayName("Late / out-of-order events")
    class LateEvents {

        @Test
        @DisplayName("Late events (older bucket) are silently dropped without corrupting state")
        void lateEventDropped() {
            aggregator.process(event(100.0, 70)); // bucket 60
            aggregator.process(event(200.0, 50)); // LATE: bucket 0 — should be dropped

            Optional<Candle> candle = aggregator.forceFlush();
            assertThat(candle.get().open()).isCloseTo(100.0, within(0.5));
            assertThat(candle.get().volume()).isEqualTo(1); // only the first event counted
        }
    }

    @Nested
    @DisplayName("Stale flush (scheduler-driven)")
    class StaleFlush {

        @Test
        @DisplayName("flushIfStale emits candle when wall clock has passed the bucket")
        void schedulerFlushesStaleCandle() {
            aggregator.process(event(100.0, 0)); // bucket 0

            // Simulate scheduler running when it's already second 65 (bucket 60)
            aggregator.flushIfStale(65);

            assertThat(completedCandles).hasSize(1);
            assertThat(completedCandles.get(0).time()).isEqualTo(0L);
        }

        @Test
        @DisplayName("flushIfStale does nothing if current candle's bucket is still active")
        void doesNothingForActiveCandle() {
            aggregator.process(event(100.0, 10)); // bucket 0

            // Only 30 seconds in — bucket 0 is still active
            aggregator.flushIfStale(30);

            assertThat(completedCandles).isEmpty();
        }
    }

    @Nested
    @DisplayName("Force flush (shutdown)")
    class ForceFlush {

        @Test
        @DisplayName("forceFlush returns and emits the current open candle")
        void forceFlushEmitsCandle() {
            aggregator.process(event(100.0, 10));
            aggregator.process(event(200.0, 20));

            Optional<Candle> result = aggregator.forceFlush();

            assertThat(result).isPresent();
            assertThat(completedCandles).hasSize(1); // callback also invoked
        }

        @Test
        @DisplayName("forceFlush on empty aggregator returns empty")
        void forceFlushEmptyIsEmpty() {
            Optional<Candle> result = aggregator.forceFlush();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("forceFlush twice does not emit duplicate candles")
        void forceFlushTwiceIsIdempotent() {
            aggregator.process(event(100.0, 10));
            aggregator.forceFlush();
            aggregator.forceFlush();

            assertThat(completedCandles).hasSize(1);
        }
    }
}
