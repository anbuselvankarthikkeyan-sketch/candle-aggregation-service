package com.candle.service;

import com.candle.aggregator.CandleAggregator;
import com.candle.event.BidAskEvent;
import com.candle.model.Interval;
import com.candle.store.CandleStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central orchestration service that:
 * <ul>
 *   <li>Maintains a pool of {@link CandleAggregator}s, one per (symbol, interval) pair</li>
 *   <li>Routes incoming {@link BidAskEvent}s to all relevant aggregators</li>
 *   <li>Runs a scheduled flush to finalize stale open candles</li>
 *   <li>Stores completed candles to {@link CandleStore}</li>
 * </ul>
 *
 * <p>New symbols are auto-registered on first event — no configuration restart required.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final CandleStore candleStore;

    /**
     * Key: "SYMBOL::INTERVAL_LABEL" → aggregator
     * ConcurrentHashMap for safe concurrent reads + computeIfAbsent writes
     */
    private final ConcurrentMap<String, CandleAggregator> aggregators = new ConcurrentHashMap<>();

    public AggregationService(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    /**
     * Ingest a single bid/ask event.
     * Fans out to all interval aggregators for this symbol, creating them if needed.
     *
     * @param event The incoming market data event
     */
    public void ingest(BidAskEvent event) {
        log.debug("Ingesting event: symbol={} bid={} ask={} ts={}",
                event.symbol(), event.bid(), event.ask(), event.timestamp());

        for (Interval interval : Interval.values()) {
            String key = aggregatorKey(event.symbol(), interval);
            CandleAggregator aggregator = aggregators.computeIfAbsent(key, k -> {
                log.info("Creating new aggregator for symbol={} interval={}", event.symbol(), interval.getLabel());
                return new CandleAggregator(
                        event.symbol(),
                        interval,
                        (intervalLabel, candle) -> candleStore.save(event.symbol(), intervalLabel, candle)
                );
            });
            aggregator.process(event);
        }
    }

    /**
     * Scheduled flush — runs every second to finalize open candles that have passed their bucket boundary.
     * This ensures candles are emitted even when event flow temporarily stops.
     */
    @Scheduled(fixedRateString = "${candle.flush.interval-ms:1000}")
    public void flushStaleCandles() {
        long nowSeconds = Instant.now().getEpochSecond();
        aggregators.values().forEach(agg -> agg.flushIfStale(nowSeconds));
    }

    /**
     * On shutdown, force-flush all open candles so no data is lost.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutdown: force-flushing {} aggregators", aggregators.size());
        aggregators.values().forEach(agg -> agg.forceFlush().ifPresent(candle ->
                log.info("Flushed on shutdown: symbol={} interval={} time={}",
                        agg.getSymbol(), agg.getInterval().getLabel(), candle.time())));
    }

    /**
     * Returns the list of symbols currently known to the aggregation engine.
     */
    public List<String> activeSymbols() {
        return aggregators.keySet().stream()
                .map(key -> key.split("::")[0])
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Returns the total number of active aggregators (symbols × intervals).
     */
    public int aggregatorCount() {
        return aggregators.size();
    }

    private static String aggregatorKey(String symbol, Interval interval) {
        return symbol + "::" + interval.getLabel();
    }
}
