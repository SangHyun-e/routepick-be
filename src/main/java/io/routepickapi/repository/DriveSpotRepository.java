package io.routepickapi.repository;

import io.routepickapi.entity.drive.DriveSpot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriveSpotRepository extends JpaRepository<DriveSpot, Long> {

    List<DriveSpot> findByIsActiveTrue();

    long countByIsActiveTrue();

    long countByIsActiveTrueAndThemesContainingIgnoreCase(String theme);
}
