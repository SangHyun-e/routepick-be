package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.course.CourseStop;
import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.recommendation.CourseStopResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CourseStopResponseMapper {

    private final PoiTagLabelMapper tagLabelMapper;

    public CourseStopResponseMapper(PoiTagLabelMapper tagLabelMapper) {
        this.tagLabelMapper = tagLabelMapper;
    }

    public CourseStopResponse map(CourseStop stop) {
        if (stop == null || stop.poi() == null) {
            return new CourseStopResponse(0, null, 0, 0, null, List.of(), 0, 0, 0, 0, 0);
        }

        Poi poi = stop.poi();
        Duration stay = stop.stayDuration() == null ? Duration.ZERO : stop.stayDuration();
        Duration segment = stop.segmentDuration() == null ? Duration.ZERO : stop.segmentDuration();
        List<String> tags = tagLabelMapper.toDisplayTags(poi);
        String type = tagLabelMapper.toDisplayType(poi);

        return new CourseStopResponse(
            stop.order(),
            poi.name(),
            poi.lat(),
            poi.lng(),
            type,
            tags,
            stay.toMinutes(),
            poi.viewScore(),
            poi.driveSuitability(),
            stop.segmentDistanceKm(),
            segment.toMinutes()
        );
    }
}
