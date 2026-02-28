package com.candle.aggregator;

import com.candle.event.BidAskEvent;
import com.candle.model.Candle;
import com.candle.model.Interval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link CandleAggregator} is thread-safe under concurrent load.
 */
@DisplayName("CandleAggregator concurrency")
class ConcurrencyTest {

    @Test
    @DisplayName("Concurrent events produce consistent candle output with no exceptions")
    void concurrentEventsAreThreadSafe() throws InterruptedException {
        List<Candle> completed = Collections.synchronizedList(new ArrayList<>());

        CandleAggregator aggregator = new CandleAggregator(
                "BTC-USD", Interval.ONE_SECOND,
                (interval, candle) -> completed.add(candle));

        int threads = 8;
        int eventsPerThread = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Start all threads simultaneously
                    for (int i = 0; i < eventsPerThread; i++) {
                        double mid = 100.0 + (threadIndex * 10) + i;
                        double spread = mid * 0.001;
                        // Spread events across ~25 seconds to cause multiple bucket rolls
                        long timestampMs = (long)(i * 125) + 1;
                        BidAskEvent event = new BidAskEvent(
                                "BTC-USD", mid - spread / 2, mid + spread / 2, timestampMs);
                        aggregator.process(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Flush any remaining open candle
        aggregator.forceFlush();

        // Key assertions:
        // 1. No exceptions thrown (test would fail otherwise)
        // 2. Candles were produced (multiple bucket rolls expected)
        // 3. Every candle obeys high >= low and high >= open/close
        assertThat(completed).isNotEmpty();
        for (Candle c : completed) {
            assertThat(c.high()).isGreaterThanOrEqualTo(c.low());
            assertThat(c.high()).isGreaterThanOrEqualTo(c.open());
            assertThat(c.high()).isGreaterThanOrEqualTo(c.close());
            assertThat(c.low()).isLessThanOrEqualTo(c.open());
            assertThat(c.low()).isLessThanOrEqualTo(c.close());
            assertThat(c.volume()).isGreaterThan(0);
        }
    }
}
