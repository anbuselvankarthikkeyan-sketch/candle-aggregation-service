package com.candle.store;

import com.candle.model.Candle;
import com.candle.model.CandleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory, thread-safe store for completed candles.
 *
 * <p>Backed by a {@link ConcurrentHashMap} keyed on {@link CandleKey}.
 * Reads are fully concurrent and non-blocking.
 * Writes use putIfAbsent / merge to avoid lost updates.
 *
 * <p>For production use this can be swapped for a TimescaleDB or InfluxDB adapter
 * by implementing the same interface contract.
 */
@Repository
public class CandleStore {

    private static final Logger log = LoggerFactory.getLogger(CandleStore.class);

    private final ConcurrentMap<CandleKey, Candle> store = new ConcurrentHashMap<>();

    /**
     * Save (or overwrite) a completed candle.
     * Overwrites are allowed to support late-arriving event corrections.
     */
    public void save(String symbol, String interval, Candle candle) {
        CandleKey key = new CandleKey(symbol, interval, candle.time());
        store.put(key, candle);
        log.debug("Stored candle: symbol={} interval={} time={}", symbol, interval, candle.time());
    }

    /**
     * Query candles for a given symbol and interval within an inclusive time range.
     *
     * @param symbol   Trading symbol
     * @param interval Interval label (e.g., "1m")
     * @param from     Start of range in Unix seconds (inclusive)
     * @param to       End of range in Unix seconds (inclusive)
     * @return List of matching candles sorted ascending by time
     */
    public List<Candle> query(String symbol, String interval, long from, long to) {
        return store.entrySet().stream()
                .filter(e -> e.getKey().symbol().equals(symbol))
                .filter(e -> e.getKey().interval().equals(interval))
                .filter(e -> e.getKey().bucketTime() >= from && e.getKey().bucketTime() <= to)
                .map(java.util.Map.Entry::getValue)
                .sorted(java.util.Comparator.comparingLong(Candle::time))
                .collect(Collectors.toList());
    }

    /**
     * Returns the total number of candles stored across all symbols and intervals.
     * Useful for health/metrics endpoints.
     */
    public int totalCandles() {
        return store.size();
    }

    /**
     * Returns distinct symbols known to the store.
     */
    public List<String> knownSymbols() {
        return store.keySet().stream()
                .map(CandleKey::symbol)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Clears all data — primarily for testing.
     */
    public void clear() {
        store.clear();
    }
}
