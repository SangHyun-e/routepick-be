package io.routepickapi.repository;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndStatus(String email, UserStatus status);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

    boolean existsByNickname(String nickname);

    boolean existsByEmailAndStatus(String email, UserStatus status);

    @Query("""
        select u
          from User u
         where (:keyword is null
            or :keyword = ''
            or lower(u.email) like lower(concat('%', :keyword, '%'))
            or lower(u.nickname) like lower(concat('%', :keyword, '%')))
        """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
