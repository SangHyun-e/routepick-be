package io.routepickapi.infrastructure.client.routing.dto;

public record Coordinate(double longitude, double latitude) {
    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range");
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range");
        }
    }
}
