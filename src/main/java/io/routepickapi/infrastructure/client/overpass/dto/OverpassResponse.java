package io.routepickapi.infrastructure.client.overpass.dto;

import java.util.List;

public record OverpassResponse(List<OverpassElement> elements) {
}
