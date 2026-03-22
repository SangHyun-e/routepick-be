package io.routepickapi.infrastructure.client.routing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public record Summary(
    double distance,
    double duration,
    @JsonAlias("way_points") List<Integer> wayPoints
) {
}
