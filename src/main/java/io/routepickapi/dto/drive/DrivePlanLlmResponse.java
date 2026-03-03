package io.routepickapi.dto.drive;

import java.util.List;

public record DrivePlanLlmResponse(
    String courseName,
    String planReason,
    List<DrivePlanLlmStop> stops
) {
    public record DrivePlanLlmStop(
        int order,
        String name,
        String address,
        double lat,
        double lng,
        String placeUrl,
        String reason,
        boolean openNowEstimated
    ) {
    }
}
