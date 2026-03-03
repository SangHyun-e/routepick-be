package io.routepickapi.controller;

import io.routepickapi.dto.drive.DrivePlanRequest;
import io.routepickapi.dto.drive.DrivePlanResponse;
import io.routepickapi.service.DrivePlanService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/drive")
public class DrivePlanController {

    private final DrivePlanService drivePlanService;

    @Operation(summary = "드라이브 코스 AI 추천", description = "출발/도착 키워드와 테마로 코스를 추천합니다."
        + " openNowEstimated는 영업중 추정을 의미합니다.")
    @PostMapping("/plan")
    public DrivePlanResponse plan(@Valid @RequestBody DrivePlanRequest request) {
        log.info("POST /drive/plan - theme={}, openNowOnly={}", request.theme(), request.openNowOnly());
        return drivePlanService.plan(request);
    }
}
