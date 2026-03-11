package io.routepickapi.controller;

import io.routepickapi.dto.admin.AdminDashboardResponse;
import io.routepickapi.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "관리자 대시보드", description = "가입 현황과 최근 가입자 정보를 제공합니다.")
    @GetMapping
    public AdminDashboardResponse dashboard() {
        return adminDashboardService.getDashboard();
    }
}
