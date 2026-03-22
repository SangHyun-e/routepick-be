package io.routepickapi.infrastructure.client.overpass.dto;

import java.util.List;
import java.util.Map;

public record OverpassElement(
    String type,
    long id,
    Double lat,
    Double lon,
    List<Long> nodes,
    Map<String, String> tags
) {
}
