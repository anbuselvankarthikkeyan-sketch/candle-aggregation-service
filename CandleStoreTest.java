package com.candle.store;

import com.candle.model.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CandleStore")
class CandleStoreTest {

    private CandleStore store;

    private static Candle candle(long time) {
        return new Candle(time, 100.0, 110.0, 90.0, 105.0, 5);
    }

    @BeforeEach
    void setUp() {
        store = new CandleStore();
    }

    @Test
    @DisplayName("save and query within range returns correct candles sorted by time")
    void saveAndQueryBasic() {
        store.save("BTC-USD", "1m", candle(0));
        store.save("BTC-USD", "1m", candle(60));
        store.save("BTC-USD", "1m", candle(120));

        List<Candle> result = store.query("BTC-USD", "1m", 0, 120);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).time()).isEqualTo(0);
        assertThat(result.get(1).time()).isEqualTo(60);
        assertThat(result.get(2).time()).isEqualTo(120);
    }

    @Test
    @DisplayName("query respects from/to bounds inclusively")
    void queryBounds() {
        store.save("BTC-USD", "1m", candle(0));
        store.save("BTC-USD", "1m", candle(60));
        store.save("BTC-USD", "1m", candle(120));

        List<Candle> result = store.query("BTC-USD", "1m", 60, 60);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).time()).isEqualTo(60);
    }

    @Test
    @DisplayName("query returns empty list when no candles match")
    void queryEmpty() {
        List<Candle> result = store.query("BTC-USD", "1m", 0, 3600);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("different symbols are stored independently")
    void symbolIsolation() {
        store.save("BTC-USD", "1m", candle(0));
        store.save("ETH-USD", "1m", candle(0));

        assertThat(store.query("BTC-USD", "1m", 0, 60)).hasSize(1);
        assertThat(store.query("ETH-USD", "1m", 0, 60)).hasSize(1);
        assertThat(store.query("SOL-USD", "1m", 0, 60)).isEmpty();
    }

    @Test
    @DisplayName("different intervals are stored independently")
    void intervalIsolation() {
        store.save("BTC-USD", "1m", candle(0));
        store.save("BTC-USD", "1h", candle(0));

        assertThat(store.query("BTC-USD", "1m", 0, 60)).hasSize(1);
        assertThat(store.query("BTC-USD", "1h", 0, 60)).hasSize(1);
        assertThat(store.query("BTC-USD", "5m", 0, 60)).isEmpty();
    }

    @Test
    @DisplayName("saving the same key twice overwrites the first candle")
    void saveOverwrites() {
        store.save("BTC-USD", "1m", new Candle(0, 100, 110, 90, 105, 5));
        store.save("BTC-USD", "1m", new Candle(0, 200, 220, 180, 210, 10));

        List<Candle> result = store.query("BTC-USD", "1m", 0, 0);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).open()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("totalCandles returns correct count")
    void totalCandles() {
        assertThat(store.totalCandles()).isEqualTo(0);
        store.save("BTC-USD", "1m", candle(0));
        store.save("BTC-USD", "1m", candle(60));
        store.save("ETH-USD", "1h", candle(0));
        assertThat(store.totalCandles()).isEqualTo(3);
    }

    @Test
    @DisplayName("knownSymbols returns all distinct symbols sorted")
    void knownSymbols() {
        store.save("SOL-USD", "1m", candle(0));
        store.save("BTC-USD", "1m", candle(0));
        store.save("ETH-USD", "1m", candle(0));
        store.save("BTC-USD", "1h", candle(0));

        List<String> symbols = store.knownSymbols();
        assertThat(symbols).containsExactly("BTC-USD", "ETH-USD", "SOL-USD");
    }

    @Test
    @DisplayName("clear empties the store")
    void clearEmptiesStore() {
        store.save("BTC-USD", "1m", candle(0));
        store.clear();
        assertThat(store.totalCandles()).isEqualTo(0);
    }
}
