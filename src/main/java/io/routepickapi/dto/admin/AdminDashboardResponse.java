package io.routepickapi.dto.admin;

import io.routepickapi.dto.user.AdminUserListItemResponse;
import java.time.LocalDate;
import java.util.List;

public record AdminDashboardResponse(
    long totalUsers,
    List<AdminUserListItemResponse> recentUsers,
    List<SignupCount> signupsByDay
) {
    public record SignupCount(LocalDate date, long count) {
    }
}
