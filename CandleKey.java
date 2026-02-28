package com.candle.model;

/**
 * Composite key for uniquely identifying a candle by symbol, interval, and bucket start time.
 * Uses Java record for automatic equals/hashCode — safe for use as ConcurrentHashMap key.
 *
 * @param symbol      Trading symbol (e.g., "BTC-USD")
 * @param interval    Interval string (e.g., "1m", "1h")
 * @param bucketTime  Bucket start time in Unix seconds (time-aligned)
 */
public record CandleKey(String symbol, String interval, long bucketTime) {}
