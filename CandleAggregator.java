package com.candle.aggregator;

import com.candle.event.BidAskEvent;
import com.candle.model.Candle;
import com.candle.model.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * Aggregates a stream of {@link BidAskEvent}s for a single (symbol, interval) pair
 * into completed {@link Candle}s.
 *
 * <p>Thread-safety is achieved via a per-aggregator {@link ReentrantLock} so that
 * multiple symbols/intervals can be processed in parallel without contention.
 *
 * <p>A candle is considered complete ("flushed") when:
 * <ul>
 *   <li>An event arrives with a timestamp belonging to a <em>newer</em> bucket, or</li>
 *   <li>The external flush scheduler calls {@link #flushIfStale(long)}</li>
 * </ul>
 */
public class CandleAggregator {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregator.class);

    private final String symbol;
    private final Interval interval;
    private final BiConsumer<String, Candle> onCandleComplete;
    private final ReentrantLock lock = new ReentrantLock();

    /** The candle currently being built. Null if no events received yet. */
    private MutableCandle currentCandle;

    /**
     * @param symbol           The trading symbol this aggregator handles
     * @param interval         The time interval to aggregate over
     * @param onCandleComplete Callback invoked with the interval label and completed candle
     */
    public CandleAggregator(String symbol, Interval interval, BiConsumer<String, Candle> onCandleComplete) {
        this.symbol = symbol;
        this.interval = interval;
        this.onCandleComplete = onCandleComplete;
    }

    /**
     * Process a new bid/ask event.
     * If the event falls into a new time bucket, the current candle is flushed first.
     */
    public void process(BidAskEvent event) {
        double price = event.midPrice();
        long bucket = interval.bucketStart(event.timestampSeconds());

        lock.lock();
        try {
            if (currentCandle == null) {
                // First event ever for this aggregator
                currentCandle = new MutableCandle(bucket, price);
                log.debug("[{}@{}] Started first candle at bucket={}", symbol, interval.getLabel(), bucket);
            } else if (bucket > currentCandle.getBucketTime()) {
                // Event belongs to a newer bucket — flush and start fresh
                flush();
                currentCandle = new MutableCandle(bucket, price);
                log.debug("[{}@{}] Rolled to new candle at bucket={}", symbol, interval.getLabel(), bucket);
            } else if (bucket == currentCandle.getBucketTime()) {
                // Same bucket — update in place
                currentCandle.update(price);
            } else {
                // Late/out-of-order event — log and skip (could backfill in extended version)
                log.warn("[{}@{}] Late event dropped: eventBucket={}, currentBucket={}",
                        symbol, interval.getLabel(), bucket, currentCandle.getBucketTime());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Flush the current candle if its bucket is older than the given wall-clock time.
     * Called by the external scheduler to ensure candles are emitted even when event flow slows.
     *
     * @param nowSeconds Current wall-clock time in Unix seconds
     */
    public void flushIfStale(long nowSeconds) {
        lock.lock();
        try {
            if (currentCandle == null) return;
            long currentBucket = currentCandle.getBucketTime();
            long expectedCurrentBucket = interval.bucketStart(nowSeconds);

            if (currentBucket < expectedCurrentBucket) {
                log.debug("[{}@{}] Scheduler flushing stale candle at bucket={}",
                        symbol, interval.getLabel(), currentBucket);
                flush();
                currentCandle = null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Force-flush the current open candle without starting a new one.
     * Useful for graceful shutdown.
     */
    public Optional<Candle> forceFlush() {
        lock.lock();
        try {
            if (currentCandle == null) return Optional.empty();
            Candle candle = currentCandle.snapshot();
            onCandleComplete.accept(interval.getLabel(), candle);
            currentCandle = null;
            return Optional.of(candle);
        } finally {
            lock.unlock();
        }
    }

    /** Must be called while holding the lock. */
    private void flush() {
        Candle candle = currentCandle.snapshot();
        log.info("[{}@{}] Candle complete: time={} O={} H={} L={} C={} V={}",
                symbol, interval.getLabel(),
                candle.time(), candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
        onCandleComplete.accept(interval.getLabel(), candle);
    }

    public String getSymbol() {
        return symbol;
    }

    public Interval getInterval() {
        return interval;
    }
}
