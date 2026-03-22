package io.routepickapi.mapper.recommendation;

import io.routepickapi.domain.poi.Poi;
import io.routepickapi.dto.recommendation.RecommendedStopResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendedStopResponseMapper {

    private final PoiTagLabelMapper tagLabelMapper;

    public RecommendedStopResponseMapper(PoiTagLabelMapper tagLabelMapper) {
        this.tagLabelMapper = tagLabelMapper;
    }

    public RecommendedStopResponse map(Poi poi) {
        if (poi == null) {
            return new RecommendedStopResponse(null, 0, 0, null, List.of(), 0, 0);
        }

        String type = tagLabelMapper.toDisplayType(poi);
        List<String> tags = tagLabelMapper.toDisplayTags(poi);

        return new RecommendedStopResponse(
            poi.name(),
            poi.lat(),
            poi.lng(),
            type,
            tags,
            poi.viewScore(),
            poi.driveSuitability()
        );
    }
}
