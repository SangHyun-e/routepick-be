package io.routepickapi.infrastructure.client.weather.dto;

public record WeatherItem(
    String category,
    String obsrValue,
    String fcstValue,
    String fcstDate,
    String fcstTime
) {
}
