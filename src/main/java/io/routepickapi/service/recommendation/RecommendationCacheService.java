package io.routepickapi.service.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.routepickapi.dto.place.KakaoPlaceSearchResponse.KakaoPlaceDocument;
import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.infrastructure.client.overpass.dto.OverpassElement;
import io.routepickapi.infrastructure.client.tour.dto.TourItem;
import io.routepickapi.service.recommendation.pipeline.DriveCourseResult;
import io.routepickapi.service.recommendation.pipeline.RawPoiBundle;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationCacheService {

    private static final Duration ROUTE_METRICS_TTL = Duration.ofMinutes(10);
    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration CANDIDATE_BUNDLE_TTL = Duration.ofMinutes(3);
    private static final Duration RESULT_TTL = Duration.ofSeconds(60);

    @org.springframework.beans.factory.annotation.Value("${recommendation.cache.ttl-seconds:60}")
    private long resultTtlSeconds;

    @org.springframework.beans.factory.annotation.Value("${recommendation.cache.short-ttl-seconds:10}")
    private long resultShortTtlSeconds;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RouteMetricsService.RouteLegMetrics getRouteMetrics(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putRouteMetrics(String key, RouteMetricsService.RouteLegMetrics metrics) {
        writeValue(key, metrics, ROUTE_METRICS_TTL);
    }

    public List<KakaoPlaceDocument> getKakaoPlaces(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putKakaoPlaces(String key, List<KakaoPlaceDocument> places) {
        writeValue(key, places, SEARCH_CACHE_TTL);
    }

    public List<TourItem> getTourItems(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putTourItems(String key, List<TourItem> items) {
        writeValue(key, items, SEARCH_CACHE_TTL);
    }

    public List<OverpassElement> getOverpassElements(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putOverpassElements(String key, List<OverpassElement> elements) {
        writeValue(key, elements, SEARCH_CACHE_TTL);
    }

    public RawPoiBundle getPoiBundle(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putPoiBundle(String key, RawPoiBundle bundle) {
        writeValue(key, bundle, CANDIDATE_BUNDLE_TTL);
    }

    public List<CandidatePlace> getCandidatePlaces(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putCandidatePlaces(String key, List<CandidatePlace> candidates) {
        writeValue(key, candidates, CANDIDATE_BUNDLE_TTL);
    }

    public DriveCourseResult getDriveCourseResult(String key) {
        return readValue(key, new TypeReference<>() {});
    }

    public void putDriveCourseResult(String key, DriveCourseResult result) {
        writeValue(key, result, Duration.ofSeconds(resultTtlSeconds));
    }

    public void putDriveCourseResult(String key, DriveCourseResult result, Duration ttl) {
        writeValue(key, result, ttl == null ? RESULT_TTL : ttl);
    }

    public Duration resultTtl() {
        return Duration.ofSeconds(resultTtlSeconds);
    }

    public Duration shortResultTtl() {
        return Duration.ofSeconds(resultShortTtlSeconds);
    }

    private <T> T readValue(String key, TypeReference<T> type) {
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, type);
        } catch (Exception ex) {
            log.debug("Cache read failed - key={}", key, ex);
            return null;
        }
    }

    private void writeValue(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (JsonProcessingException ex) {
            log.debug("Cache write failed - key={}", key, ex);
        }
    }
}
