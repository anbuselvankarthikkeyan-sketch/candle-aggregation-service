# Candle Aggregation Service

A production-grade Spring Boot service that ingests a real-time stream of bid/ask market data, aggregates it into OHLCV candlestick format across multiple symbols and timeframes, and exposes a TradingView-compatible history REST API.

---

## Table of Contents

- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [API Reference](#api-reference)
- [Running the Service](#running-the-service)
- [Running Tests](#running-tests)
- [Configuration](#configuration)
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)
- [Extending the Service](#extending-the-service)

---

## Architecture

```
┌────────────────────────────────────────────────────────┐
│              MarketDataGenerator                        │
│  (Random walk, @Scheduled, configurable rate)          │
└─────────────────────┬──────────────────────────────────┘
                      │ BidAskEvent (symbol, bid, ask, ts)
                      ▼
┌────────────────────────────────────────────────────────┐
│              AggregationService                         │
│  - Fan-out: one CandleAggregator per (symbol×interval) │
│  - @Scheduled stale-flush every 1s                     │
│  - @PreDestroy force-flush on shutdown                 │
└─────────────────────┬──────────────────────────────────┘
                      │ Candle (time, O, H, L, C, V)
                      ▼
┌────────────────────────────────────────────────────────┐
│              CandleStore                                │
│  ConcurrentHashMap<CandleKey, Candle>                  │
│  Key = (symbol, interval, bucketTime)                  │
└─────────────────────┬──────────────────────────────────┘
                      │
                      ▼
┌────────────────────────────────────────────────────────┐
│   REST API  GET /history?symbol=&interval=&from=&to=   │
│   Response: TradingView UDF format {s,t,o,h,l,c,v}    │
└────────────────────────────────────────────────────────┘
```

### Key Design Principle: One Aggregator Per (Symbol × Interval)

Each `CandleAggregator` is a completely independent state machine for exactly one (symbol, interval) pair. It holds a `ReentrantLock` to protect its internal `MutableCandle`. This means:

- `BTC-USD@1m` and `ETH-USD@1m` never contend with each other
- `BTC-USD@1m` and `BTC-USD@1h` never contend with each other
- Maximum concurrency is achieved without a global lock

---

## Project Structure

```
src/main/java/com/candle/
├── CandleAggregationApplication.java   Entry point
├── aggregator/
│   ├── CandleAggregator.java           Core OHLC aggregation per (symbol, interval)
│   └── MutableCandle.java              Mutable accumulator during aggregation
├── config/
│   └── AppConfig.java                  Scheduling configuration
├── controller/
│   ├── HistoryController.java          GET /history endpoint
│   ├── HistoryResponse.java            TradingView UDF response DTO
│   └── StatusController.java           /ping, /status, /symbols, /intervals
├── event/
│   └── BidAskEvent.java                Input domain record
├── generator/
│   └── MarketDataGenerator.java        Simulated random walk feed
├── model/
│   ├── Candle.java                     Immutable OHLCV record
│   ├── CandleKey.java                  Composite map key (symbol, interval, bucket)
│   └── Interval.java                   Supported timeframes enum
├── service/
│   └── AggregationService.java         Orchestration, routing, scheduled flush
└── store/
    └── CandleStore.java                Thread-safe in-memory candle storage

src/test/java/com/candle/
├── aggregator/
│   ├── CandleAggregatorTest.java       Core OHLC logic (most important tests)
│   ├── AggregationServiceTest.java     Routing and fan-out tests
│   ├── BidAskEventTest.java            Input validation tests
│   ├── ConcurrencyTest.java            Thread safety under concurrent load
│   └── IntervalTest.java               Bucket alignment and label parsing
├── controller/
│   └── HistoryControllerTest.java      REST API integration tests (MockMvc)
└── store/
    └── CandleStoreTest.java            Storage query and isolation tests
```

---

## How It Works

### Candle Lifecycle

1. A `BidAskEvent` arrives with `(symbol, bid, ask, timestamp_ms)`
2. Mid-price is computed as `(bid + ask) / 2`
3. The event is routed to all `Interval` aggregators for that symbol
4. Each aggregator computes `bucketStart = (timestampSeconds / intervalSeconds) * intervalSeconds`
5. If the event is in the current bucket → update O/H/L/C/V
6. If the event is in a newer bucket → flush current candle to store, start new one
7. A `@Scheduled` flush runs every second to emit any candle whose bucket has elapsed (handles end-of-stream)
8. On shutdown (`@PreDestroy`), all open candles are force-flushed

### Mid-Price

Since raw events provide `bid` and `ask`, OHLC is computed from the **mid-price**: `(bid + ask) / 2`. This is the industry-standard approach for tick-data aggregation.

### Volume

Volume is synthetic — it counts the number of ticks (events) within each bucket. In a production system with real trade data, actual trade quantities would be summed.

---

## API Reference

### `GET /history`

Fetch historical OHLCV candles.

**Parameters:**

| Parameter  | Type   | Required | Description                        |
|------------|--------|----------|------------------------------------|
| `symbol`   | String | Yes      | Trading pair (e.g., `BTC-USD`)    |
| `interval` | String | Yes      | Timeframe (e.g., `1s`, `1m`, `1h`)|
| `from`     | Long   | Yes      | Start time in Unix seconds         |
| `to`       | Long   | Yes      | End time in Unix seconds           |

**Example Request:**
```
GET /history?symbol=BTC-USD&interval=1m&from=1620000000&to=1620000600
```

**Success Response (`200 OK`):**
```json
{
  "s": "ok",
  "t": [1620000000, 1620000060, 1620000120],
  "o": [29500.5, 29501.0, 29498.0],
  "h": [29510.0, 29505.0, 29502.0],
  "l": [29490.0, 29500.0, 29495.0],
  "c": [29505.0, 29502.0, 29499.0],
  "v": [10, 8, 12]
}
```

**No Data Response:**
```json
{ "s": "no_data", "t": [], "o": [], "h": [], "l": [], "c": [], "v": [] }
```

**Error Response (`400 Bad Request`):**
```json
{ "s": "error: Unsupported interval: 2m. Supported: 1s, 5s, ...", ... }
```

**Supported intervals:** `1s`, `5s`, `15s`, `1m`, `5m`, `15m`, `1h`

---

### `GET /ping`
```json
{ "status": "ok" }
```

### `GET /status`
```json
{
  "status": "ok",
  "timestamp": 1710000000,
  "aggregators": 28,
  "activeSymbols": ["BTC-USD", "ETH-USD", "SOL-USD", "BNB-USD"],
  "totalCandlesStored": 3412,
  "totalEventsGenerated": 12800,
  "supportedIntervals": ["1s", "5s", "15s", "1m", "5m", "15m", "1h"]
}
```

### `GET /symbols`
```json
["BNB-USD", "BTC-USD", "ETH-USD", "SOL-USD"]
```

### `GET /intervals`
```json
["1s", "5s", "15s", "1m", "5m", "15m", "1h"]
```

### `GET /actuator/health`
Spring Boot Actuator health endpoint.

---

## Running the Service

### Prerequisites

- Java 21+
- Maven 3.8+

### Build & Run

```bash
# Clone the repo
git clone <your-repo-url>
cd candle-aggregation-service

# Build
mvn clean package -DskipTests

# Run
java -jar target/candle-aggregation-service-1.0.0.jar
```

### Quick Smoke Test

After startup, wait ~10 seconds for candles to accumulate, then:

```bash
# Get current time
NOW=$(date +%s)
TEN_MIN_AGO=$((NOW - 600))

# Query BTC-USD 1-minute candles
curl "http://localhost:8080/history?symbol=BTC-USD&interval=1m&from=${TEN_MIN_AGO}&to=${NOW}"

# Check service status
curl http://localhost:8080/status

# Health check
curl http://localhost:8080/actuator/health
```

---

## Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CandleAggregatorTest

# Run with verbose output
mvn test -pl . --no-transfer-progress
```

### Test Coverage Summary

| Test Class                | What It Tests                                          |
|---------------------------|--------------------------------------------------------|
| `CandleAggregatorTest`    | OHLC correctness, rollover, late events, flush logic   |
| `IntervalTest`            | Bucket alignment math, label parsing                   |
| `BidAskEventTest`         | Input validation, mid-price, timestamp conversion      |
| `CandleStoreTest`         | Storage, query ranges, symbol/interval isolation       |
| `AggregationServiceTest`  | Fan-out routing, multi-symbol independence             |
| `ConcurrencyTest`         | Thread safety under 8-thread concurrent load           |
| `HistoryControllerTest`   | Full REST API integration via MockMvc (7 scenarios)    |

---

## Configuration

All settings are in `src/main/resources/application.properties`:

```properties
# Simulated market data
candle.generator.enabled=true          # false to disable in tests
candle.generator.interval-ms=200       # emit one tick per symbol every 200ms
candle.generator.symbols=BTC-USD,ETH-USD,SOL-USD,BNB-USD

# Candle flush scheduler
candle.flush.interval-ms=1000          # check for stale candles every 1s
```

---

## Design Decisions & Trade-offs

### 1. In-Memory Storage
**Decision:** `ConcurrentHashMap` instead of a database.

**Rationale:** The spec says in-memory is the minimum viable approach. The store is a swappable `@Repository` — plugging in TimescaleDB or InfluxDB means implementing the same `save`/`query` contract with a JDBC or client adapter.

**Trade-off:** Data is lost on restart. In production, a time-series database is essential.

### 2. Per-Aggregator ReentrantLock (Not Global Lock)
**Decision:** Each `CandleAggregator` owns its own `ReentrantLock`.

**Rationale:** A global lock would serialize all event processing. With per-aggregator locks, 4 symbols × 7 intervals = 28 aggregators can all run truly concurrently.

**Trade-off:** Slightly more complex than synchronizing on `this`, but necessary for high throughput.

### 3. Event-Driven Flush + Scheduled Flush
**Decision:** Candles are flushed both when a new-bucket event arrives AND by a 1-second scheduler.

**Rationale:** Event-driven flush alone fails at end-of-stream (the last candle never arrives if no newer event comes). The scheduler guarantees candles are emitted even in quiet markets.

**Trade-off:** The scheduler adds 0–1 interval latency to candle finalization.

### 4. Late Event Dropping
**Decision:** Events with timestamps older than the current open bucket are logged and discarded.

**Rationale:** Simplest correct behavior. The alternative is a bounded reorder buffer, but that adds complexity and memory pressure for marginal benefit with low-latency feeds.

**Trade-off:** Very late events (>1 interval late) are silently dropped. Acceptable for simulated or low-latency feeds.

### 5. Mid-Price for OHLC
**Decision:** `(bid + ask) / 2` as the price for all OHLC fields.

**Rationale:** Industry standard for tick-data aggregation when trade prices are unavailable.

### 6. Java Records for Domain Objects
**Decision:** `BidAskEvent`, `Candle`, `CandleKey` are Java records.

**Rationale:** Records give `equals`/`hashCode`/`toString` for free, making `CandleKey` a safe map key and `Candle` a proper value object with no boilerplate.

---

## Extending the Service

### Add a New Interval
Edit `Interval.java` and add a new enum entry:
```java
TWO_HOURS("2h", 7200),
```
No other changes needed — the service auto-creates aggregators for all intervals.

### Add a New Symbol
Just send events for the new symbol. The service auto-registers aggregators on the first event for any symbol.

### Replace the Generator with Real Data
Replace `MarketDataGenerator` with any source that calls `aggregationService.ingest(event)`:

```java
// Kafka consumer
@KafkaListener(topics = "market-data")
public void onMessage(BidAskEvent event) {
    aggregationService.ingest(event);
}

// WebSocket handler
webSocketClient.onMessage(json -> {
    BidAskEvent event = parse(json);
    aggregationService.ingest(event);
});
```

### Replace In-Memory Store with TimescaleDB
Create a new `@Repository` class that implements the same `save`/`query` methods using Spring JDBC or JPA, and annotate `CandleStore` with `@Primary` or remove it.

---

## Future Improvements

- **Persistent storage:** TimescaleDB or InfluxDB for historical replay and crash recovery
- **WebSocket feed:** Push completed candles to connected clients in real time
- **Metrics:** Micrometer gauges for candles/sec, events/sec, lag
- **Reorder buffer:** Handle slightly out-of-order events within a configurable window
- **Kafka integration:** Replace the generator with a Kafka consumer for production feeds
- **API pagination:** Limit + offset for large time ranges
