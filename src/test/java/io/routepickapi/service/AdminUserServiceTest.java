package io.routepickapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.entity.user.User;
import io.routepickapi.entity.user.UserStatus;
import io.routepickapi.entity.user.UserStatusHistory;
import io.routepickapi.repository.UserRepository;
import io.routepickapi.repository.UserStatusHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatusHistoryRepository historyRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userRepository, historyRepository,
            refreshTokenService);
    }

    @Test
    void suspendedStatusDeletesRefreshTokens() {
        User user = new User("suspended@example.com", "hash", "suspended");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.activate();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        adminUserService.updateStatus(1L, UserStatus.BLOCKED, "policy", 99L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(refreshTokenService).deleteAllForUser(1L);

        ArgumentCaptor<UserStatusHistory> captor = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captor.getValue().getToStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void deletedStatusStoresHistory() {
        User user = new User("deleted@example.com", "hash", "deleted");
        ReflectionTestUtils.setField(user, "id", 2L);
        user.activate();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        adminUserService.updateStatus(2L, UserStatus.DELETED, "cleanup", 100L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(refreshTokenService).deleteAllForUser(2L);
        ArgumentCaptor<UserStatusHistory> captor = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captor.getValue().getToStatus()).isEqualTo(UserStatus.DELETED);
    }

    @Test
    void deletedUserCannotBeRestored() {
        User user = new User("gone@example.com", "hash", "gone");
        ReflectionTestUtils.setField(user, "id", 3L);
        user.delete();

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class,
            () -> adminUserService.updateStatus(3L, UserStatus.ACTIVE, null, 1L));

        assertThat(exception.getType()).isEqualTo(ErrorType.USER_STATUS_CHANGE_NOT_ALLOWED);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void pendingUserCanBeActivated() {
        User user = new User("pending@example.com", "hash", "pending");
        ReflectionTestUtils.setField(user, "id", 4L);

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        adminUserService.updateStatus(4L, UserStatus.ACTIVE, "approve", 7L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        ArgumentCaptor<UserStatusHistory> captor = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void pendingUserCanBeDeleted() {
        User user = new User("pending-delete@example.com", "hash", "pending-delete");
        ReflectionTestUtils.setField(user, "id", 5L);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        adminUserService.updateStatus(5L, UserStatus.DELETED, "cleanup", 8L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        ArgumentCaptor<UserStatusHistory> captor = ArgumentCaptor.forClass(UserStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(UserStatus.DELETED);
        verify(refreshTokenService).deleteAllForUser(5L);
    }
}
