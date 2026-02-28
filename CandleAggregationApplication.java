package com.candle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CandleAggregationApplication {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregationApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CandleAggregationApplication.class, args);
        log.info("Candle Aggregation Service started.");
        log.info("History API:  GET http://localhost:8080/history?symbol=BTC-USD&interval=1m&from=<unix>&to=<unix>");
        log.info("Status:       GET http://localhost:8080/status");
        log.info("Health:       GET http://localhost:8080/actuator/health");
    }
}
