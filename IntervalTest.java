package com.candle.aggregator;

import com.candle.model.Interval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Interval enum")
class IntervalTest {

    @ParameterizedTest(name = "bucketStart({1}s) with interval={0} → {2}s")
    @CsvSource({
            "ONE_SECOND,   10,    10",
            "ONE_SECOND,   10,    10",
            "ONE_MINUTE,   75,    60",
            "ONE_MINUTE,   119,   60",
            "ONE_MINUTE,   120,   120",
            "ONE_HOUR,     3601,  3600",
            "FIVE_MINUTES, 301,   300",
            "FIVE_MINUTES, 600,   600",
    })
    @DisplayName("bucketStart aligns timestamps correctly")
    void bucketStartAligns(String intervalName, long timestamp, long expectedBucket) {
        Interval interval = Interval.valueOf(intervalName);
        assertThat(interval.bucketStart(timestamp)).isEqualTo(expectedBucket);
    }

    @Test
    @DisplayName("fromLabel returns correct interval for known labels")
    void fromLabelKnown() {
        assertThat(Interval.fromLabel("1m")).contains(Interval.ONE_MINUTE);
        assertThat(Interval.fromLabel("1h")).contains(Interval.ONE_HOUR);
        assertThat(Interval.fromLabel("5s")).contains(Interval.FIVE_SECONDS);
    }

    @Test
    @DisplayName("fromLabel returns empty for unknown labels")
    void fromLabelUnknown() {
        assertThat(Interval.fromLabel("2m")).isEmpty();
        assertThat(Interval.fromLabel("")).isEmpty();
        assertThat(Interval.fromLabel("garbage")).isEmpty();
    }

    @Test
    @DisplayName("All intervals have unique labels")
    void allLabelsUnique() {
        long uniqueCount = java.util.Arrays.stream(Interval.values())
                .map(Interval::getLabel)
                .distinct()
                .count();
        assertThat(uniqueCount).isEqualTo(Interval.values().length);
    }
}
