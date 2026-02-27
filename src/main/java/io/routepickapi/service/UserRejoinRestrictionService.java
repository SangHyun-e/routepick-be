package io.routepickapi.service;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRejoinRestrictionService {

    private static final int REJOIN_RESTRICTION_DAYS = 7;
    private static final HexFormat HEX = HexFormat.of();

    private final UserRepository userRepository;

    public void validateRejoinAllowed(String email) {
        String emailHash = hashEmail(email);
        LocalDateTime now = LocalDateTime.now();
        boolean restricted = userRepository
            .existsByDeletedEmailHashAndRejoinRestrictedUntilAfterAndRejoinRestrictionReleasedAtIsNull(
                emailHash,
                now
            );
        if (restricted) {
            throw new CustomException(ErrorType.USER_REJOIN_RESTRICTED,
                "탈퇴 후 7일간 재가입할 수 없습니다.");
        }
    }

    public void applyRestriction(User user, String email) {
        String emailHash = hashEmail(email);
        LocalDateTime restrictedUntil = LocalDateTime.now().plusDays(REJOIN_RESTRICTION_DAYS);
        user.applyRejoinRestriction(emailHash, restrictedUntil);
    }

    public void releaseRestriction(User user, Long adminUserId, String reason) {
        user.releaseRejoinRestriction(adminUserId, reason);
    }

    private String hashEmail(String email) {
        String normalized = normalizeEmail(email);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("invalid email");
        }
        return email.trim().toLowerCase();
    }
}
