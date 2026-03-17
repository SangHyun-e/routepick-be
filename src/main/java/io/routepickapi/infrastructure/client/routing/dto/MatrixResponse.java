package io.routepickapi.infrastructure.client.routing.dto;

import java.util.List;

public record MatrixResponse(
    List<List<Double>> durations,
    List<List<Double>> distances
) {
}
