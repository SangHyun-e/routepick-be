package io.routepickapi.entity.user;

import io.routepickapi.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "uk_users_email", columnList = "email", unique = true),
        @Index(name = "idx_users_status_created", columnList = "status, created_at")
    }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 40, unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private UserAuthProvider authProvider = UserAuthProvider.LOCAL;

    @Column(name = "profile_complete", nullable = false)
    private boolean profileComplete = true;

    public User(String email, String passwordHash, String nickname) {
        this(email, passwordHash, nickname, UserAuthProvider.LOCAL);
    }

    public User(String email, String passwordHash, String nickname, UserAuthProvider authProvider) {
        setEmail(email);
        setPasswordHash(passwordHash);
        setNickname(nickname);
        setAuthProvider(authProvider);
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 255) {
            throw new IllegalArgumentException("invalid email");
        }
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            this.passwordHash = null;
            return;
        }
        if (passwordHash.length() > 255) {
            throw new IllegalArgumentException("invalid passwordHash");
        }
        this.passwordHash = passwordHash;
    }

    public void setNickname(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > 40) {
            throw new IllegalArgumentException("invalid nickname");
        }
        this.nickname = nickname;
    }

    public void setRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("invalid role");
        }
        this.role = role;
    }

    public void setAuthProvider(UserAuthProvider authProvider) {
        if (authProvider == null) {
            throw new IllegalArgumentException("invalid authProvider");
        }
        this.authProvider = authProvider;
    }

    public void markProfileIncomplete() {
        this.profileComplete = false;
    }

    public void markProfileComplete() {
        this.profileComplete = true;
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void delete() {
        Long token = id != null ? id : System.currentTimeMillis();
        setEmail("deleted-" + token + "@routepick.local");
        setNickname("탈퇴회원" + token);
        setPasswordHash("deleted-" + token);
        this.status = UserStatus.DELETED;
    }

    public void markPending() {
        this.status = UserStatus.PENDING;
    }
}
