package io.routepickapi.service.recommendation.pipeline;

public record WeatherSnapshot(
    double score,
    Double temperatureCelsius,
    boolean precipitation
) {
}
