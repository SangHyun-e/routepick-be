package io.routepickapi.infrastructure.client.routing.dto;

import java.util.List;

public record MatrixRequest(
    List<List<Double>> locations,
    List<String> metrics,
    String units
) {
}
