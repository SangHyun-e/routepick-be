package io.routepickapi.repository;

import io.routepickapi.entity.user.UserStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {

    Page<UserStatusHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
