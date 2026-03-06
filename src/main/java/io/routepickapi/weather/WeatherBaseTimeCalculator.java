package io.routepickapi.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WeatherBaseTimeCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");
    private static final List<LocalTime> NOWCAST_TIMES = buildTimes(0);
    private static final List<LocalTime> FORECAST_TIMES = buildTimes(30);

    public BaseDateTime forUltraShortNow() {
        return forUltraShortNow(LocalDateTime.now(KST));
    }

    public BaseDateTime forUltraShortNow(LocalDateTime nowKst) {
        return resolveBase(nowKst.minusMinutes(30), NOWCAST_TIMES);
    }

    public BaseDateTime forUltraShortForecast() {
        return forUltraShortForecast(LocalDateTime.now(KST));
    }

    public BaseDateTime forUltraShortForecast(LocalDateTime nowKst) {
        return resolveBase(nowKst.minusMinutes(30), FORECAST_TIMES);
    }

    private BaseDateTime resolveBase(LocalDateTime adjusted, List<LocalTime> baseTimes) {
        LocalDate date = adjusted.toLocalDate();
        LocalTime time = adjusted.toLocalTime();

        LocalTime selected = null;
        for (LocalTime candidate : baseTimes) {
            if (candidate.isAfter(time)) {
                break;
            }
            selected = candidate;
        }

        if (selected == null) {
            selected = baseTimes.getLast();
            date = date.minusDays(1);
        }

        return new BaseDateTime(date.format(DATE_FORMAT), selected.format(TIME_FORMAT));
    }

    private static List<LocalTime> buildTimes(int minute) {
        List<LocalTime> times = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            times.add(LocalTime.of(hour, minute));
        }
        return List.copyOf(times);
    }

    public record BaseDateTime(String baseDate, String baseTime) {
    }
}
