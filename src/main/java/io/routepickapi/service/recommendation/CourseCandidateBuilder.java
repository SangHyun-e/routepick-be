package io.routepickapi.service.recommendation;

import io.routepickapi.dto.recommendation.CandidatePlace;
import io.routepickapi.dto.recommendation.CourseCandidate;
import io.routepickapi.dto.recommendation.GeoPoint;
import io.routepickapi.dto.recommendation.ScoreDetail;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CourseCandidateBuilder {

    private static final int MAX_COURSES = 60;
    private static final int MAX_NEIGHBORS = 6;
    private static final double MAX_EDGE_DISTANCE_KM = 15.0;
    private static final double MAX_START_DISTANCE_KM = 25.0;
    private static final double MAX_END_DISTANCE_KM = 25.0;

    public List<CourseCandidate> buildCourses(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> candidates,
        int minStops,
        int maxStops
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<CandidatePlace, List<CandidatePlace>> adjacency = buildAdjacency(candidates);
        List<CourseCandidate> courses = new ArrayList<>();

        for (CandidatePlace start : candidates) {
            if (distanceKm(origin, start) > MAX_START_DISTANCE_KM) {
                continue;
            }
            Deque<CandidatePlace> path = new ArrayDeque<>();
            Set<CandidatePlace> visited = new HashSet<>();
            dfs(origin, destination, start, adjacency, path, visited, courses, minStops, maxStops);
            if (courses.size() >= MAX_COURSES) {
                break;
            }
        }

        log.info("코스 후보 생성 완료 - count={}", courses.size());
        return courses;
    }

    private void dfs(
        GeoPoint origin,
        GeoPoint destination,
        CandidatePlace current,
        Map<CandidatePlace, List<CandidatePlace>> adjacency,
        Deque<CandidatePlace> path,
        Set<CandidatePlace> visited,
        List<CourseCandidate> courses,
        int minStops,
        int maxStops
    ) {
        if (courses.size() >= MAX_COURSES) {
            return;
        }

        path.addLast(current);
        visited.add(current);

        if (path.size() >= minStops && distanceKm(destination, current) <= MAX_END_DISTANCE_KM) {
            courses.add(buildCourse(origin, destination, path));
        }

        if (path.size() < maxStops) {
            List<CandidatePlace> neighbors = adjacency.getOrDefault(current, List.of());
            for (CandidatePlace next : neighbors) {
                if (visited.contains(next)) {
                    continue;
                }
                dfs(origin, destination, next, adjacency, path, visited, courses, minStops, maxStops);
                if (courses.size() >= MAX_COURSES) {
                    break;
                }
            }
        }

        visited.remove(current);
        path.removeLast();
    }

    private Map<CandidatePlace, List<CandidatePlace>> buildAdjacency(List<CandidatePlace> candidates) {
        Map<CandidatePlace, List<CandidatePlace>> adjacency = new HashMap<>();
        for (CandidatePlace candidate : candidates) {
            List<CandidatePlace> neighbors = candidates.stream()
                .filter(other -> !other.equals(candidate))
                .filter(other -> distanceKm(candidate, other) <= MAX_EDGE_DISTANCE_KM)
                .sorted((first, second) -> Double.compare(distanceKm(candidate, first), distanceKm(candidate, second)))
                .limit(MAX_NEIGHBORS)
                .collect(Collectors.toList());
            adjacency.put(candidate, neighbors);
        }
        return adjacency;
    }

    private CourseCandidate buildCourse(
        GeoPoint origin,
        GeoPoint destination,
        Deque<CandidatePlace> path
    ) {
        List<CandidatePlace> stops = new ArrayList<>(path);
        double totalDistanceKm = calculateTotalDistance(origin, destination, stops);
        int estimatedMinutes = GeoUtils.estimateMinutes(totalDistanceKm);
        return new CourseCandidate(
            UUID.randomUUID().toString(),
            stops,
            totalDistanceKm,
            estimatedMinutes,
            0,
            ScoreDetail.empty(),
            List.of()
        );
    }

    private double calculateTotalDistance(
        GeoPoint origin,
        GeoPoint destination,
        List<CandidatePlace> stops
    ) {
        if (stops.isEmpty()) {
            return 0;
        }

        double distance = distanceKm(origin, stops.getFirst());
        for (int index = 0; index < stops.size() - 1; index++) {
            distance += distanceKm(stops.get(index), stops.get(index + 1));
        }
        distance += distanceKm(destination, stops.getLast());
        return distance;
    }

    private double distanceKm(GeoPoint point, CandidatePlace place) {
        return GeoUtils.distanceKm(point, new GeoPoint(place.x(), place.y()));
    }

    private double distanceKm(CandidatePlace first, CandidatePlace second) {
        return GeoUtils.distanceKm(
            new GeoPoint(first.x(), first.y()),
            new GeoPoint(second.x(), second.y())
        );
    }
}
