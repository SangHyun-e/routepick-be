package io.routepickapi.dto.recommendation;

import io.routepickapi.dto.course.DriveMood;
import io.routepickapi.dto.course.DriveRouteStyle;
import io.routepickapi.dto.course.DriveStopType;
import java.util.List;

public record DrivePreference(
    List<DriveMood> moods,
    List<DriveStopType> stopTypes,
    List<DriveRouteStyle> routeStyles,
    boolean autoRecommend
) {
}
