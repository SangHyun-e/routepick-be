package io.routepickapi.service;

import io.routepickapi.dto.admin.AdminDashboardResponse;
import io.routepickapi.dto.admin.AdminDashboardResponse.SignupCount;
import io.routepickapi.dto.user.AdminUserListItemResponse;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int RECENT_USER_LIMIT = 5;
    private static final int SIGNUP_DAYS = 7;

    private final UserRepository userRepository;

    public AdminDashboardResponse getDashboard() {
        long totalUsers = userRepository.count();

        List<AdminUserListItemResponse> recentUsers = userRepository
            .findAll(PageRequest.of(0, RECENT_USER_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(AdminUserListItemResponse::from)
            .getContent();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(SIGNUP_DAYS - 1L);
        LocalDateTime startAt = startDate.atStartOfDay();
        LocalDateTime endAt = today.plusDays(1).atStartOfDay();

        List<User> users = userRepository.findByCreatedAtBetween(startAt, endAt);
        Map<LocalDate, Long> counts = users.stream()
            .collect(Collectors.groupingBy(user -> user.getCreatedAt().toLocalDate(),
                Collectors.counting()));

        List<SignupCount> signupsByDay = IntStream.range(0, SIGNUP_DAYS)
            .mapToObj(index -> {
                LocalDate date = startDate.plusDays(index);
                long count = counts.getOrDefault(date, 0L);
                return new SignupCount(date, count);
            })
            .toList();

        return new AdminDashboardResponse(totalUsers, recentUsers, signupsByDay);
    }
}
