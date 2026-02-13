package io.routepickapi.entity.user;

import io.routepickapi.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "user_identities",
    indexes = {
        @Index(name = "uk_user_identities_provider_user_id", columnList = "provider_user_id", unique = true),
        @Index(name = "idx_user_identities_user_provider", columnList = "user_id, provider")
    }
)
public class UserIdentity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserIdentityProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100, unique = true)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    public UserIdentity(User user, UserIdentityProvider provider, String providerUserId,
        String email) {
        setUser(user);
        setProvider(provider);
        setProviderUserId(providerUserId);
        this.email = email;
    }

    public void setUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("invalid user");
        }
        this.user = user;
    }

    public void setProvider(UserIdentityProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("invalid provider");
        }
        this.provider = provider;
    }

    public void setProviderUserId(String providerUserId) {
        if (providerUserId == null || providerUserId.isBlank() || providerUserId.length() > 100) {
            throw new IllegalArgumentException("invalid providerUserId");
        }
        this.providerUserId = providerUserId;
    }
}
