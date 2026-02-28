package com.candle.aggregator;

import com.candle.model.Candle;

/**
 * Mutable accumulator for an in-progress candle.
 *
 * This class is NOT thread-safe by itself — callers must synchronize on a per-key basis.
 * Kept separate from the immutable {@link Candle} record to clearly separate
 * mutable aggregation state from the immutable domain model.
 */
class MutableCandle {

    private final long bucketTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    MutableCandle(long bucketTime, double firstPrice) {
        this.bucketTime = bucketTime;
        this.open = firstPrice;
        this.high = firstPrice;
        this.low = firstPrice;
        this.close = firstPrice;
        this.volume = 1;
    }

    /**
     * Incorporate a new price tick into this candle.
     */
    void update(double price) {
        if (price > high) high = price;
        if (price < low) low = price;
        close = price;
        volume++;
    }

    long getBucketTime() {
        return bucketTime;
    }

    /**
     * Produce an immutable snapshot of the current state.
     */
    Candle snapshot() {
        return new Candle(bucketTime, open, high, low, close, volume);
    }
}
