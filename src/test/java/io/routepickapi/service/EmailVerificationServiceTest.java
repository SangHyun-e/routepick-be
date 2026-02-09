package io.routepickapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String KEY_PREFIX = "rp:email:verify:";
    private static final String TRIES_PREFIX = "rp:email:verify:tries:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        emailVerificationService = new EmailVerificationService(redisTemplate, userRepository, emailSender);
        ReflectionTestUtils.setField(emailVerificationService, "ttlSeconds", 600L);
        ReflectionTestUtils.setField(emailVerificationService, "codeLength", 6);
        ReflectionTestUtils.setField(emailVerificationService, "maxTries", 2);
    }

    @Test
    void sendCodeStoresCodeInRedisAndCallsEmailSender() {
        String emailRaw = "Test@Example.com";
        String email = "test@example.com";
        User user = new User(email, "hash", "nick");

        when(userRepository.existsByEmailAndStatus(email, UserStatus.ACTIVE)).thenReturn(false);
        when(userRepository.findByEmailAndStatus(email, UserStatus.PENDING))
            .thenReturn(Optional.of(user));

        emailVerificationService.sendCode(emailRaw);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).set(keyCaptor.capture(), codeCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(KEY_PREFIX + email);
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(600L));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");

        verify(redisTemplate).delete(TRIES_PREFIX + email);
        verify(emailSender).sendVerificationCode(eq(user), eq(codeCaptor.getValue()), eq(600L));
    }

    @Test
    void confirmCodeActivatesUserAndDeletesKeys() {
        String emailRaw = "Test@Example.com";
        String email = "test@example.com";
        String code = "123456";
        User user = new User(email, "hash", "nick");

        when(userRepository.existsByEmailAndStatus(email, UserStatus.ACTIVE)).thenReturn(false);
        when(valueOperations.get(KEY_PREFIX + email)).thenReturn(code);
        when(userRepository.findByEmailAndStatus(email, UserStatus.PENDING))
            .thenReturn(Optional.of(user));

        emailVerificationService.confirmCode(emailRaw, code);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(redisTemplate).delete(KEY_PREFIX + email);
        verify(redisTemplate).delete(TRIES_PREFIX + email);
    }

    @Test
    void wrongCodeIncreasesTriesAndBlocksAfterMaxTries() {
        String emailRaw = "Test@Example.com";
        String email = "test@example.com";
        String storedCode = "123456";

        ReflectionTestUtils.setField(emailVerificationService, "maxTries", 1);
        when(userRepository.existsByEmailAndStatus(email, UserStatus.ACTIVE)).thenReturn(false);
        when(valueOperations.get(KEY_PREFIX + email)).thenReturn(storedCode);
        when(valueOperations.increment(TRIES_PREFIX + email)).thenReturn(2L);

        CustomException exception = assertThrows(CustomException.class,
            () -> emailVerificationService.confirmCode(emailRaw, "000000"));

        assertThat(exception.getType()).isEqualTo(ErrorType.AUTH_EMAIL_VERIFY_TOO_MANY_TRIES);
        verify(valueOperations).increment(TRIES_PREFIX + email);
        verify(redisTemplate).delete(KEY_PREFIX + email);
        verify(redisTemplate).delete(TRIES_PREFIX + email);
    }
}
