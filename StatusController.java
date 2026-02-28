package com.candle.controller;

import com.candle.generator.MarketDataGenerator;
import com.candle.model.Interval;
import com.candle.service.AggregationService;
import com.candle.store.CandleStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Operational endpoints for health monitoring and service introspection.
 */
@RestController
public class StatusController {

    private final AggregationService aggregationService;
    private final CandleStore candleStore;
    private final MarketDataGenerator generator;

    public StatusController(AggregationService aggregationService,
                            CandleStore candleStore,
                            MarketDataGenerator generator) {
        this.aggregationService = aggregationService;
        this.candleStore = candleStore;
        this.generator = generator;
    }

    /**
     * Simple liveness probe.
     * GET /ping → {"status": "ok"}
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Detailed service status.
     * GET /status → aggregator counts, candle counts, event counts, etc.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "timestamp", Instant.now().getEpochSecond(),
                "aggregators", aggregationService.aggregatorCount(),
                "activeSymbols", aggregationService.activeSymbols(),
                "totalCandlesStored", candleStore.totalCandles(),
                "totalEventsGenerated", generator.getEventCount(),
                "supportedIntervals", Arrays.asList(Interval.supportedLabels())
        ));
    }

    /**
     * Lists all symbols currently known to the store.
     * GET /symbols → ["BTC-USD", "ETH-USD", ...]
     */
    @GetMapping("/symbols")
    public ResponseEntity<List<String>> symbols() {
        return ResponseEntity.ok(candleStore.knownSymbols());
    }

    /**
     * Lists all supported intervals.
     * GET /intervals → ["1s", "5s", ...]
     */
    @GetMapping("/intervals")
    public ResponseEntity<List<String>> intervals() {
        return ResponseEntity.ok(
                Arrays.stream(Interval.values()).map(Interval::getLabel).toList()
        );
    }
}
