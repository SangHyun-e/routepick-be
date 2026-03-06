package io.routepickapi.repository;

import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.util.List;

public interface WeatherRepository {

    List<WeatherItem> fetchUltraShortNow(BaseDateTime baseDateTime, int nx, int ny);

    List<WeatherItem> fetchUltraShortForecast(BaseDateTime baseDateTime, int nx, int ny);

    record WeatherItem(
        String category,
        String obsrValue,
        String fcstValue,
        String fcstDate,
        String fcstTime
    ) {
    }
}
