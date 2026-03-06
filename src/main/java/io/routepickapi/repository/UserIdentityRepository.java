package io.routepickapi.repository;

import io.routepickapi.entity.user.UserIdentity;
import io.routepickapi.entity.user.UserIdentityProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndProviderUserId(UserIdentityProvider provider,
        String providerUserId);

    Optional<UserIdentity> findByUserIdAndProvider(Long userId, UserIdentityProvider provider);
}
