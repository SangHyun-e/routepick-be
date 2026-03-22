package io.routepickapi.infrastructure.client.weather.dto;

public record WeatherResponse(WeatherHeader header, WeatherBody body) {
}
