package io.routepickapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.routepickapi.common.error.CustomException;
import io.routepickapi.dto.weather.DriveWeatherResponse;
import io.routepickapi.repository.WeatherRepository;
import io.routepickapi.repository.WeatherRepository.WeatherItem;
import io.routepickapi.weather.GridConverter;
import io.routepickapi.weather.GridConverter.GridPoint;
import io.routepickapi.weather.WeatherBaseTimeCalculator;
import io.routepickapi.weather.WeatherBaseTimeCalculator.BaseDateTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveWeatherService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_PREFIX = "weather:drive:";
    private static final double DEFAULT_LAT = 37.5665;
    private static final double DEFAULT_LNG = 126.9780;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    private final GridConverter gridConverter = new GridConverter();
    private final WeatherBaseTimeCalculator baseTimeCalculator = new WeatherBaseTimeCalculator();
    private final WeatherRepository weatherRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DriveWeatherResponse getDriveMessage(Double lat, Double lng, boolean fallbackRequested) {
        boolean usedFallbackLocation = fallbackRequested;

        double safeLat = lat == null ? DEFAULT_LAT : lat;
        double safeLng = lng == null ? DEFAULT_LNG : lng;
        if (!isValidCoordinate(safeLat, safeLng)) {
            safeLat = DEFAULT_LAT;
            safeLng = DEFAULT_LNG;
            usedFallbackLocation = true;
        }

        GridPoint gridPoint = gridConverter.toGrid(safeLat, safeLng);
        String cacheKey = CACHE_PREFIX + gridPoint.nx() + ":" + gridPoint.ny();

        DriveWeatherResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached.withUsedFallbackLocation(usedFallbackLocation);
        }

        DriveWeatherResponse response = fetchDriveWeather(gridPoint, usedFallbackLocation);
        if (shouldCache(response)) {
            writeCache(cacheKey, response.withUsedFallbackLocation(false));
        }
        return response;
    }

    private DriveWeatherResponse fetchDriveWeather(GridPoint gridPoint, boolean usedFallbackLocation) {
        try {
            LocalDateTime now = LocalDateTime.now(KST);
            BaseDateTime nowBase = baseTimeCalculator.forUltraShortNow(now);
            BaseDateTime forecastBase = baseTimeCalculator.forUltraShortForecast(now);

            List<WeatherItem> nowItems = fetchNowItemsWithFallback(nowBase, now, gridPoint);
            List<WeatherItem> forecastItems = fetchForecastItemsWithFallback(
                forecastBase,
                now,
                gridPoint
            );

            WeatherSnapshot snapshot = buildSnapshot(nowItems, forecastItems, now);
            if (snapshot == null) {
                return DriveWeatherResponse.fallback(usedFallbackLocation);
            }

            String message = buildMessage(snapshot, now.getHour());
            return new DriveWeatherResponse(
                message,
                snapshot.temperature(),
                snapshot.precipitationType(),
                snapshot.skyStatus(),
                snapshot.windSpeed(),
                usedFallbackLocation
            );
        } catch (CustomException ex) {
            log.warn("Drive weather fallback due to custom exception: {}", ex.getMessage());
            return DriveWeatherResponse.fallback(usedFallbackLocation);
        } catch (Exception ex) {
            log.warn("Drive weather fallback due to unexpected error", ex);
            return DriveWeatherResponse.fallback(usedFallbackLocation);
        }
    }

    private List<WeatherItem> fetchNowItemsWithFallback(
        BaseDateTime baseDateTime,
        LocalDateTime now,
        GridPoint gridPoint
    ) {
        List<WeatherItem> items = weatherRepository.fetchUltraShortNow(
            baseDateTime,
            gridPoint.nx(),
            gridPoint.ny()
        );
        if (!items.isEmpty()) {
            return items;
        }

        BaseDateTime fallbackBase = baseTimeCalculator.forUltraShortNow(now.minusMinutes(90));
        List<WeatherItem> fallbackItems = weatherRepository.fetchUltraShortNow(
            fallbackBase,
            gridPoint.nx(),
            gridPoint.ny()
        );
        if (fallbackItems.isEmpty()) {
            log.warn("Weather nowcast empty: baseDate={}, baseTime={}, nx={}, ny={}",
                baseDateTime.baseDate(),
                baseDateTime.baseTime(),
                gridPoint.nx(),
                gridPoint.ny());
        }
        return fallbackItems;
    }

    private List<WeatherItem> fetchForecastItemsWithFallback(
        BaseDateTime baseDateTime,
        LocalDateTime now,
        GridPoint gridPoint
    ) {
        List<WeatherItem> items = weatherRepository.fetchUltraShortForecast(
            baseDateTime,
            gridPoint.nx(),
            gridPoint.ny()
        );
        if (!items.isEmpty()) {
            return items;
        }

        BaseDateTime fallbackBase = baseTimeCalculator.forUltraShortForecast(now.minusMinutes(90));
        List<WeatherItem> fallbackItems = weatherRepository.fetchUltraShortForecast(
            fallbackBase,
            gridPoint.nx(),
            gridPoint.ny()
        );
        if (fallbackItems.isEmpty()) {
            log.warn("Weather forecast empty: baseDate={}, baseTime={}, nx={}, ny={}",
                baseDateTime.baseDate(),
                baseDateTime.baseTime(),
                gridPoint.nx(),
                gridPoint.ny());
        }
        return fallbackItems;
    }

    private WeatherSnapshot buildSnapshot(
        List<WeatherItem> nowItems,
        List<WeatherItem> forecastItems,
        LocalDateTime now
    ) {
        if (nowItems == null || nowItems.isEmpty()) {
            return null;
        }

        Double temperature = parseDouble(findObsValue(nowItems, "T1H"));
        Integer precipitationType = parseInteger(findObsValue(nowItems, "PTY"));
        Double windSpeed = parseDouble(findObsValue(nowItems, "WSD"));
        Integer sky = parseInteger(findClosestForecastValue(forecastItems, "SKY", now));

        if (temperature == null && precipitationType == null && windSpeed == null && sky == null) {
            return null;
        }

        return new WeatherSnapshot(temperature, precipitationType, windSpeed, sky);
    }

    private String findObsValue(List<WeatherItem> items, String category) {
        return items.stream()
            .filter(item -> category.equals(item.category()))
            .map(WeatherItem::obsrValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String findClosestForecastValue(
        List<WeatherItem> items,
        String category,
        LocalDateTime now
    ) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        return items.stream()
            .filter(item -> category.equals(item.category()))
            .map(item -> new ForecastCandidate(item, parseForecastDateTime(item)))
            .filter(candidate -> candidate.time() != null)
            .min(Comparator.comparingLong(candidate -> Math.abs(Duration.between(now, candidate.time()).toMinutes())))
            .map(candidate -> candidate.item().fcstValue())
            .orElse(null);
    }

    private LocalDateTime parseForecastDateTime(WeatherItem item) {
        if (item == null || item.fcstDate() == null || item.fcstTime() == null) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(item.fcstDate(), DATE_FORMAT);
            LocalTime time = LocalTime.parse(item.fcstTime(), TIME_FORMAT);
            return LocalDateTime.of(date, time);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildMessage(WeatherSnapshot snapshot, int hour) {
        int precipitationType = Optional.ofNullable(snapshot.precipitationType()).orElse(0);
        int sky = Optional.ofNullable(snapshot.skyStatus()).orElse(0);
        double windSpeed = Optional.ofNullable(snapshot.windSpeed()).orElse(0.0);

        String message;
        if (isSnow(precipitationType)) {
            message = "눈이 내려요. 노면 결빙 조심하세요.";
        } else if (isRain(precipitationType)) {
            message = "비가 내려요. 미끄럼 주의!";
        } else if (windSpeed >= 10.0) {
            message = "바람이 꽤 강해요. 고속 주행은 주의하세요.";
        } else if (precipitationType == 0 && sky == 1 && windSpeed <= 6.0) {
            message = "오늘은 드라이브하기 딱 좋은 날씨예요!";
        } else if (sky == 4) {
            message = "하늘은 흐리지만 감성 드라이브에 어울려요.";
        } else if (sky == 3) {
            message = "구름이 많아요. 살짝 서늘할 수 있어요.";
        } else {
            message = "오늘도 안전운전하며 드라이브 즐겨보세요!";
        }

        message = appendTimeHint(message, hour);
        return message;
    }

    private String appendTimeHint(String message, int hour) {
        if (hour >= 0 && hour <= 5) {
            return message + " 새벽 드라이브라면 졸음운전 조심하세요.";
        }
        if (hour >= 6 && hour <= 10) {
            return message + " 상쾌한 아침 드라이브 어때요?";
        }
        if (hour >= 11 && hour <= 15) {
            return message + " 나들이하기 좋은 시간대예요.";
        }
        if (hour >= 16 && hour <= 19) {
            return message + " 해질녘 드라이브 추천해요.";
        }
        if (hour >= 20 && hour <= 23) {
            return message + " 야경 코스 어떠세요?";
        }
        return message;
    }

    private boolean isSnow(int precipitationType) {
        return precipitationType == 3 || precipitationType == 7 || precipitationType == 2 || precipitationType == 6;
    }

    private boolean isRain(int precipitationType) {
        return precipitationType == 1 || precipitationType == 5 || precipitationType == 2 || precipitationType == 6;
    }

    private boolean isValidCoordinate(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private DriveWeatherResponse readCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            DriveWeatherResponse response = objectMapper.readValue(cached, DriveWeatherResponse.class);
            return shouldCache(response) ? response : null;
        } catch (Exception ex) {
            log.warn("Failed to parse cached weather", ex);
            return null;
        }
    }

    private void writeCache(String cacheKey, DriveWeatherResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, payload, CACHE_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to cache weather payload", ex);
        }
    }

    private boolean shouldCache(DriveWeatherResponse response) {
        if (response == null) {
            return false;
        }
        return response.temperature() != null
            || response.precipitationType() != null
            || response.skyStatus() != null
            || response.windSpeed() != null;
    }

    private record WeatherSnapshot(
        Double temperature,
        Integer precipitationType,
        Double windSpeed,
        Integer skyStatus
    ) {
    }

    private record ForecastCandidate(WeatherItem item, LocalDateTime time) {
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
