package com.candle.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supported candlestick aggregation intervals.
 * Each entry maps a human-readable label to its duration in seconds.
 */
public enum Interval {

    ONE_SECOND("1s", 1),
    FIVE_SECONDS("5s", 5),
    FIFTEEN_SECONDS("15s", 15),
    ONE_MINUTE("1m", 60),
    FIVE_MINUTES("5m", 300),
    FIFTEEN_MINUTES("15m", 900),
    ONE_HOUR("1h", 3600);

    private final String label;
    private final long seconds;

    private static final Map<String, Interval> BY_LABEL = Arrays.stream(values())
            .collect(Collectors.toMap(Interval::getLabel, Function.identity()));

    Interval(String label, long seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public String getLabel() {
        return label;
    }

    public long getSeconds() {
        return seconds;
    }

    /**
     * Given a raw Unix timestamp in seconds, compute the start of the bucket it falls into.
     */
    public long bucketStart(long timestampSeconds) {
        return (timestampSeconds / seconds) * seconds;
    }

    /**
     * Look up an Interval by its label string (e.g., "1m").
     */
    public static Optional<Interval> fromLabel(String label) {
        return Optional.ofNullable(BY_LABEL.get(label));
    }

    /**
     * Returns all supported label strings for validation or documentation.
     */
    public static String[] supportedLabels() {
        return BY_LABEL.keySet().toArray(String[]::new);
    }
}
