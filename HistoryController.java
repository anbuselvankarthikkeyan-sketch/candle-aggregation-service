package com.candle.controller;

import com.candle.model.Candle;
import com.candle.model.Interval;
import com.candle.store.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing the candlestick history API.
 *
 * <pre>
 * GET /history?symbol=BTC-USD&amp;interval=1m&amp;from=1620000000&amp;to=1620000600
 * </pre>
 *
 * Response format is compatible with TradingView Lightweight Charts UDF protocol.
 */
@RestController
@RequestMapping("/history")
@CrossOrigin(origins = "*") // Allow any frontend to query — restrict in production
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final CandleStore candleStore;

    public HistoryController(CandleStore candleStore) {
        this.candleStore = candleStore;
    }

    /**
     * Fetch candlestick history for a symbol and interval within a time range.
     *
     * @param symbol   Trading pair (e.g., "BTC-USD")
     * @param interval Interval string (e.g., "1m", "1h")
     * @param from     Start time in Unix seconds (inclusive)
     * @param to       End time in Unix seconds (inclusive)
     * @return TradingView-compatible OHLCV response
     */
    @GetMapping
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to
    ) {
        log.info("History request: symbol={} interval={} from={} to={}", symbol, interval, from, to);

        // Validate interval
        Optional<Interval> parsedInterval = Interval.fromLabel(interval);
        if (parsedInterval.isEmpty()) {
            log.warn("Invalid interval requested: {}", interval);
            return ResponseEntity.badRequest()
                    .body(HistoryResponse.error("Unsupported interval: " + interval
                            + ". Supported: " + String.join(", ", Interval.supportedLabels())));
        }

        // Validate time range
        if (from > to) {
            return ResponseEntity.badRequest()
                    .body(HistoryResponse.error("'from' must be <= 'to'"));
        }

        if (symbol.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(HistoryResponse.error("Symbol must not be blank"));
        }

        // Query store
        List<Candle> candles = candleStore.query(symbol.toUpperCase(), interval, from, to);

        if (candles.isEmpty()) {
            log.debug("No candles found for symbol={} interval={} from={} to={}", symbol, interval, from, to);
            return ResponseEntity.ok(HistoryResponse.noData());
        }

        log.info("Returning {} candles for symbol={} interval={}", candles.size(), symbol, interval);
        return ResponseEntity.ok(HistoryResponse.ok(candles));
    }
}
