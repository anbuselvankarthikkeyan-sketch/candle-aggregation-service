package com.candle.generator;

import com.candle.event.BidAskEvent;
import com.candle.service.AggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulated market data generator.
 *
 * <p>Produces realistic-looking random walk bid/ask events for configured symbols
 * at a configurable rate. Enabled only when {@code candle.generator.enabled=true}.
 *
 * <p>Price walks are seeded per-symbol with independent random states so each
 * symbol behaves independently. A spread of 0.1% of price is applied to generate
 * realistic bid/ask pairs.
 */
@Component
@ConditionalOnProperty(name = "candle.generator.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(MarketDataGenerator.class);

    private final AggregationService aggregationService;

    /** Current simulated mid-price per symbol */
    private final Map<String, Double> prices;
    private final Random random = new Random();
    private final AtomicLong eventCount = new AtomicLong(0);

    // Realistic base prices for each symbol
    private static final Map<String, Double> BASE_PRICES = Map.of(
            "BTC-USD", 65000.0,
            "ETH-USD", 3500.0,
            "SOL-USD", 150.0,
            "BNB-USD", 400.0
    );

    public MarketDataGenerator(
            AggregationService aggregationService,
            @Value("${candle.generator.symbols:BTC-USD,ETH-USD}") List<String> symbols) {
        this.aggregationService = aggregationService;

        // Initialize price map from configured symbols
        this.prices = new java.util.concurrent.ConcurrentHashMap<>();
        for (String symbol : symbols) {
            double basePrice = BASE_PRICES.getOrDefault(symbol, 100.0);
            prices.put(symbol, basePrice);
        }

        log.info("MarketDataGenerator initialized with symbols: {}", symbols);
    }

    /**
     * Generate one tick per symbol on each invocation.
     * Rate is controlled by {@code candle.generator.interval-ms}.
     */
    @Scheduled(fixedRateString = "${candle.generator.interval-ms:200}")
    public void generate() {
        long now = System.currentTimeMillis();

        prices.replaceAll((symbol, currentPrice) -> {
            // Random walk: ±0.05% per tick
            double change = currentPrice * (random.nextGaussian() * 0.0005);
            double newMid = Math.max(currentPrice + change, 0.01);

            // Spread is 0.1% of price (50bps total)
            double spread = newMid * 0.001;
            double bid = newMid - spread / 2;
            double ask = newMid + spread / 2;

            BidAskEvent event = new BidAskEvent(symbol, bid, ask, now);
            aggregationService.ingest(event);

            long count = eventCount.incrementAndGet();
            if (count % 500 == 0) {
                log.info("Generated {} total events. Latest: symbol={} mid={}",
                        count, symbol, String.format("%.2f", newMid));
            }

            return newMid;
        });
    }

    /**
     * Total events generated since startup — exposed for health/metrics.
     */
    public long getEventCount() {
        return eventCount.get();
    }
}
