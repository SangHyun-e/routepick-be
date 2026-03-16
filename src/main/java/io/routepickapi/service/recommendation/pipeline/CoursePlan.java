package io.routepickapi.service.recommendation.pipeline;

import io.routepickapi.domain.poi.Poi;
import java.util.List;

public record CoursePlan(List<Poi> stops) {
}
