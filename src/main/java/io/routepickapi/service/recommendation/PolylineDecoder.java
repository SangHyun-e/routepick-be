package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.GeoPoint;
import java.util.ArrayList;
import java.util.List;

public final class PolylineDecoder {

    private PolylineDecoder() {
    }

    public static List<GeoPoint> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < encoded.length()) {
            int[] latResult = decodeChunk(encoded, index);
            lat += latResult[0];
            index = latResult[1];
            int[] lngResult = decodeChunk(encoded, index);
            lng += lngResult[0];
            index = lngResult[1];
            points.add(new GeoPoint(lng / 1e5, lat / 1e5));
        }
        return points;
    }

    private static int[] decodeChunk(String encoded, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < encoded.length());
        int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[] {delta, index};
    }
}
