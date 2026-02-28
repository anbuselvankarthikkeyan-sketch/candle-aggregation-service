package com.candle.event;

/**
 * Represents a single market data tick with bid/ask prices.
 *
 * @param symbol    Trading symbol (e.g., "BTC-USD")
 * @param bid       Best bid price
 * @param ask       Best ask price
 * @param timestamp Unix timestamp in milliseconds
 */
public record BidAskEvent(String symbol, double bid, double ask, long timestamp) {

    public BidAskEvent {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol must not be blank");
        if (bid <= 0) throw new IllegalArgumentException("Bid must be positive");
        if (ask <= 0) throw new IllegalArgumentException("Ask must be positive");
        if (ask < bid) throw new IllegalArgumentException("Ask must be >= bid");
        if (timestamp <= 0) throw new IllegalArgumentException("Timestamp must be positive");
    }

    /**
     * Mid-price used as the representative price for OHLC aggregation.
     */
    public double midPrice() {
        return (bid + ask) / 2.0;
    }

    /**
     * Timestamp in Unix seconds.
     */
    public long timestampSeconds() {
        return timestamp / 1000L;
    }
}
