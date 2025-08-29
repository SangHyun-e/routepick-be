package io.routepickapi.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/_internal")
@RequiredArgsConstructor
public class DbCheckController {
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/db-check")
    public ResponseEntity<Map<String, Object>> dbCheck() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean ok = (one != null && one ==1);
            return ResponseEntity.ok(Map.of("db", ok ? "OK" : "UNKNOWN"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("db", "FAIL", "error", e.getClass().getSimpleName()));
        }
    }
}
