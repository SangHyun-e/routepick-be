package io.routepickapi.repository;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);
}
