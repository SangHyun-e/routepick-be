package io.routepickapi.infrastructure.client.routing.dto;

import java.util.List;

public record DirectionsRequest(List<List<Double>> coordinates) {
}
