package io.routepickapi.weather;

import static org.assertj.core.api.Assertions.assertThat;

import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WeatherBaseTimeCalculatorTest {

    private final WeatherBaseTimeCalculator calculator = new WeatherBaseTimeCalculator();

    @Test
    void resolvesBaseTimeAcrossMidnightForNowcast() {
        LocalDateTime now = LocalDateTime.of(2025, 3, 1, 0, 10);

        BaseDateTime base = calculator.forUltraShortNow(now);

        assertThat(base.baseDate()).isEqualTo("20250228");
        assertThat(base.baseTime()).isEqualTo("2300");
    }

    @Test
    void resolvesBaseTimeForForecast() {
        LocalDateTime now = LocalDateTime.of(2025, 3, 1, 14, 37);

        BaseDateTime base = calculator.forUltraShortForecast(now);

        assertThat(base.baseDate()).isEqualTo("20250301");
        assertThat(base.baseTime()).isEqualTo("1330");
    }
}
