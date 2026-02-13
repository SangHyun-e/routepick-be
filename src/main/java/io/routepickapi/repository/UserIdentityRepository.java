package io.routepickapi.repository;

import io.routepickapi.entity.user.UserIdentity;
import io.routepickapi.entity.user.UserIdentityProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(UserIdentityProvider provider,
        String providerUserId);

    Optional<UserIdentity> findByUserIdAndProvider(Long userId, UserIdentityProvider provider);

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO user_identities
        (user_id, provider, provider_user_id, email, created_at, updated_at, created_by, updated_by)
        VALUES (:userId, :provider, :providerUserId, :email, NOW(6), NOW(6), NULL, NULL)
        """, nativeQuery = true)
    int insertIgnore(
        @Param("userId") Long userId,
        @Param("provider") String provider,
        @Param("providerUserId") String providerUserId,
        @Param("email") String email
    );
}
