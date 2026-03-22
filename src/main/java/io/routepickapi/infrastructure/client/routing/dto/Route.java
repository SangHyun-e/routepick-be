package io.routepickapi.infrastructure.client.routing.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record Route(
    Summary summary,
    @JsonAlias("geometry") String geometry
) {
}
