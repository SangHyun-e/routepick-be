package io.routepickapi.repository;

import io.routepickapi.entity.course.CourseRecommendationSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRecommendationSaveRepository
    extends JpaRepository<CourseRecommendationSave, Long> {

    @EntityGraph(attributePaths = {"stops"})
    Page<CourseRecommendationSave> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
