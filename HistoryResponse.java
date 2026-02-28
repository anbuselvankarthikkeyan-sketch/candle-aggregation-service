package com.candle.controller;

import com.candle.model.Candle;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * REST response DTO in TradingView Lightweight Charts history format.
 *
 * <pre>
 * {
 *   "s": "ok",
 *   "t": [1620000000, ...],
 *   "o": [29500.5, ...],
 *   "h": [29510.0, ...],
 *   "l": [29490.0, ...],
 *   "c": [29505.0, ...],
 *   "v": [10, ...]
 * }
 * </pre>
 */
public record HistoryResponse(
        @JsonProperty("s") String status,
        @JsonProperty("t") List<Long> times,
        @JsonProperty("o") List<Double> opens,
        @JsonProperty("h") List<Double> highs,
        @JsonProperty("l") List<Double> lows,
        @JsonProperty("c") List<Double> closes,
        @JsonProperty("v") List<Long> volumes
) {

    /**
     * Build a successful response from a list of sorted candles.
     */
    public static HistoryResponse ok(List<Candle> candles) {
        List<Long> t = new java.util.ArrayList<>();
        List<Double> o = new java.util.ArrayList<>();
        List<Double> h = new java.util.ArrayList<>();
        List<Double> l = new java.util.ArrayList<>();
        List<Double> c = new java.util.ArrayList<>();
        List<Long> v = new java.util.ArrayList<>();

        for (Candle candle : candles) {
            t.add(candle.time());
            o.add(round(candle.open()));
            h.add(round(candle.high()));
            l.add(round(candle.low()));
            c.add(round(candle.close()));
            v.add(candle.volume());
        }

        return new HistoryResponse("ok", t, o, h, l, c, v);
    }

    /**
     * Build an empty successful response (no data in range).
     */
    public static HistoryResponse noData() {
        return new HistoryResponse("no_data",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Build an error response.
     */
    public static HistoryResponse error(String message) {
        return new HistoryResponse("error: " + message,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
