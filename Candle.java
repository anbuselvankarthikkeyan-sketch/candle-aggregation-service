package com.candle.model;

/**
 * Immutable candlestick (OHLC) record for a given time bucket.
 *
 * @param time   Bucket start time in Unix seconds
 * @param open   Opening price (first tick in bucket)
 * @param high   Highest price in bucket
 * @param low    Lowest price in bucket
 * @param close  Closing price (last tick in bucket)
 * @param volume Number of ticks (synthetic volume)
 */
public record Candle(long time, double open, double high, double low, double close, long volume) {

    public Candle {
        if (time <= 0) throw new IllegalArgumentException("Time must be positive");
        if (high < low) throw new IllegalArgumentException("High must be >= low");
        if (volume < 0) throw new IllegalArgumentException("Volume must be non-negative");
    }
}
