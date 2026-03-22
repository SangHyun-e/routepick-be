package io.routepickapi.infrastructure.client.routing.dto;

import java.util.List;

public record DirectionsResponse(List<Route> routes) {
}
