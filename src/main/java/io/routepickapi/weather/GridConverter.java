package io.routepickapi.weather;

public final class GridConverter {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_SPACING_KM = 5.0;
    private static final double STANDARD_LATITUDE_1 = 30.0;
    private static final double STANDARD_LATITUDE_2 = 60.0;
    private static final double ORIGIN_LONGITUDE = 126.0;
    private static final double ORIGIN_LATITUDE = 38.0;
    private static final double ORIGIN_X = 43.0;
    private static final double ORIGIN_Y = 136.0;
    private static final double DEGREE_TO_RADIAN = Math.PI / 180.0;

    public GridPoint toGrid(double latitude, double longitude) {
        double re = EARTH_RADIUS_KM / GRID_SPACING_KM;
        double slat1 = STANDARD_LATITUDE_1 * DEGREE_TO_RADIAN;
        double slat2 = STANDARD_LATITUDE_2 * DEGREE_TO_RADIAN;
        double olon = ORIGIN_LONGITUDE * DEGREE_TO_RADIAN;
        double olat = ORIGIN_LATITUDE * DEGREE_TO_RADIAN;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5)
            / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);

        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;

        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + latitude * DEGREE_TO_RADIAN * 0.5);
        ra = re * sf / Math.pow(ra, sn);

        double theta = longitude * DEGREE_TO_RADIAN - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.round(ra * Math.sin(theta) + ORIGIN_X);
        int ny = (int) Math.round(ro - ra * Math.cos(theta) + ORIGIN_Y);

        return new GridPoint(nx, ny);
    }

    public record GridPoint(int nx, int ny) {
    }
}
