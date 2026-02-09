package io.routepickapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String KEY_PREFIX = "rp:pwd:reset:";
    private static final String TRIES_PREFIX = "rp:pwd:reset:tries:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailSenderService emailSender;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        passwordResetService = new PasswordResetService(
            redisTemplate,
            userRepository,
            passwordEncoder,
            emailSender
        );
        ReflectionTestUtils.setField(passwordResetService, "ttlSeconds", 600L);
        ReflectionTestUtils.setField(passwordResetService, "codeLength", 6);
        ReflectionTestUtils.setField(passwordResetService, "maxTries", 2);
    }

    @Test
    void requestResetDoesNotRevealMissingUser() {
        String emailRaw = "missing@example.com";
        String email = "missing@example.com";

        when(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
            .thenReturn(Optional.empty());

        passwordResetService.requestReset(emailRaw);

        verifyNoInteractions(emailSender);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void confirmResetUpdatesPasswordAndClearsKeys() {
        String emailRaw = "User@Example.com";
        String email = "user@example.com";
        String code = "123456";
        String newPassword = "RoutePick1!";
        User user = new User(email, "oldHash", "nick");

        when(valueOperations.get(KEY_PREFIX + email)).thenReturn(code);
        when(userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE))
            .thenReturn(Optional.of(user));
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded");

        passwordResetService.confirmReset(emailRaw, code, newPassword);

        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        verify(redisTemplate).delete(KEY_PREFIX + email);
        verify(redisTemplate).delete(TRIES_PREFIX + email);
    }
}
