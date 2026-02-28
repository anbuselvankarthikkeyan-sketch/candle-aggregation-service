package com.candle.aggregator;

import com.candle.event.BidAskEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BidAskEvent")
class BidAskEventTest {

    @Test
    @DisplayName("midPrice returns average of bid and ask")
    void midPriceIsAverage() {
        BidAskEvent event = new BidAskEvent("BTC-USD", 100.0, 102.0, 1000L);
        assertThat(event.midPrice()).isEqualTo(101.0);
    }

    @Test
    @DisplayName("timestampSeconds converts millis to seconds")
    void timestampSecondsConversion() {
        BidAskEvent event = new BidAskEvent("BTC-USD", 100.0, 102.0, 75500L);
        assertThat(event.timestampSeconds()).isEqualTo(75L);
    }

    @Test
    @DisplayName("Throws if symbol is blank")
    void throwsOnBlankSymbol() {
        assertThatThrownBy(() -> new BidAskEvent("", 100.0, 102.0, 1000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws if ask < bid")
    void throwsIfAskLessThanBid() {
        assertThatThrownBy(() -> new BidAskEvent("BTC-USD", 102.0, 100.0, 1000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws if bid is zero or negative")
    void throwsOnNonPositiveBid() {
        assertThatThrownBy(() -> new BidAskEvent("BTC-USD", 0.0, 100.0, 1000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
