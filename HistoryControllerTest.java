package com.candle.controller;

import com.candle.model.Candle;
import com.candle.store.CandleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "candle.generator.enabled=false"  // Disable random generator during tests
})
@DisplayName("HistoryController integration tests")
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CandleStore candleStore;

    @BeforeEach
    void setUp() {
        candleStore.clear();
    }

    @Test
    @DisplayName("GET /history returns ok with candles in correct TradingView format")
    void historyReturnsCandles() throws Exception {
        candleStore.save("BTC-USD", "1m", new Candle(1620000000L, 29500.0, 29510.0, 29490.0, 29505.0, 10));
        candleStore.save("BTC-USD", "1m", new Candle(1620000060L, 29501.0, 29505.0, 29500.0, 29502.0, 8));

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "1620000000")
                        .param("to", "1620000120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("ok"))
                .andExpect(jsonPath("$.t", hasSize(2)))
                .andExpect(jsonPath("$.t[0]").value(1620000000))
                .andExpect(jsonPath("$.t[1]").value(1620000060))
                .andExpect(jsonPath("$.o[0]").value(29500.0))
                .andExpect(jsonPath("$.h[0]").value(29510.0))
                .andExpect(jsonPath("$.l[0]").value(29490.0))
                .andExpect(jsonPath("$.c[0]").value(29505.0))
                .andExpect(jsonPath("$.v[0]").value(10));
    }

    @Test
    @DisplayName("GET /history with no data returns no_data status")
    void historyNoData() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "1620000000")
                        .param("to", "1620000600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.s").value("no_data"))
                .andExpect(jsonPath("$.t", hasSize(0)));
    }

    @Test
    @DisplayName("GET /history with invalid interval returns 400")
    void historyInvalidInterval() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "2m")
                        .param("from", "1620000000")
                        .param("to", "1620000600"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.s", startsWith("error")));
    }

    @Test
    @DisplayName("GET /history with from > to returns 400")
    void historyInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "1620000600")
                        .param("to", "1620000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.s", startsWith("error")));
    }

    @Test
    @DisplayName("GET /history only returns candles within the requested time range")
    void historyFiltersTimeRange() throws Exception {
        candleStore.save("BTC-USD", "1m", new Candle(100L, 100.0, 110.0, 90.0, 105.0, 5));
        candleStore.save("BTC-USD", "1m", new Candle(160L, 200.0, 210.0, 190.0, 205.0, 5));
        candleStore.save("BTC-USD", "1m", new Candle(220L, 300.0, 310.0, 290.0, 305.0, 5));

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "100")
                        .param("to", "160"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.t", hasSize(2)))
                .andExpect(jsonPath("$.t[0]").value(100))
                .andExpect(jsonPath("$.t[1]").value(160));
    }

    @Test
    @DisplayName("GET /history candles are sorted ascending by time")
    void historyIsSorted() throws Exception {
        // Insert out of order
        candleStore.save("BTC-USD", "1m", new Candle(300L, 100.0, 110.0, 90.0, 105.0, 1));
        candleStore.save("BTC-USD", "1m", new Candle(60L,  100.0, 110.0, 90.0, 105.0, 1));
        candleStore.save("BTC-USD", "1m", new Candle(180L, 100.0, 110.0, 90.0, 105.0, 1));

        mockMvc.perform(get("/history")
                        .param("symbol", "BTC-USD")
                        .param("interval", "1m")
                        .param("from", "0")
                        .param("to", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.t[0]").value(60))
                .andExpect(jsonPath("$.t[1]").value(180))
                .andExpect(jsonPath("$.t[2]").value(300));
    }

    @Test
    @DisplayName("GET /ping returns ok")
    void pingOk() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    @DisplayName("GET /intervals returns all supported intervals")
    void intervalsEndpoint() throws Exception {
        mockMvc.perform(get("/intervals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItems("1s", "1m", "1h", "5m")));
    }
}
