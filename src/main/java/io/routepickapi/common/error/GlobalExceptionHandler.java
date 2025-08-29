package io.routepickapi.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/*
 * 컨트롤러 전역 예외를 JSON 바디로 통일
 * - @RestControllerAdvice: @Controller / @RestController 에서 던진 예외를 가로채 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 도메인/비즈니스 예외 -> ErrorType 기반 응답
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustom(CustomException e, HttpServletRequest req) {
        ErrorType t = e.getType();
        log.warn("CustomException : type={}, path={}, msg={}", t.code, req.getRequestURI(), e.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of(t, e.getMessage(), req.getRequestURI(), requestId(req), null);
        return ResponseEntity.status(t.httpStatus).body(body);
    }

    // 본문 검증 실패 -> 필드 상세 포함해 400 반환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<ApiErrorResponse.ApiFieldError> details = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> new ApiErrorResponse.ApiFieldError(
                fe.getField(),
                fe.getDefaultMessage(),
                safeRejectedValue(fe)
            ))
            .toList();

        ApiErrorResponse body = ApiErrorResponse.of(
            ErrorType.COMMON_INVALID_INPUT,
            null,
            req.getRequestURI(),
            requestId(req),
            details
        );
        return ResponseEntity.status(ErrorType.COMMON_INVALID_INPUT.httpStatus).body(body);
    }

    // 파라미터(쿼리/경로) 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleViolation(ConstraintViolationException e, HttpServletRequest req) {
        List<ApiErrorResponse.ApiFieldError> details = e.getConstraintViolations().stream()
            .map(v -> new ApiErrorResponse.ApiFieldError(
                v.getPropertyPath().toString(),
                v.getMessage(),
                v.getInvalidValue()
            ))
            .toList();

        ApiErrorResponse body = ApiErrorResponse.of(
            ErrorType.COMMON_INVALID_INPUT,
            null,
            req.getRequestURI(),
            requestId(req),
            details
        );
        return ResponseEntity.status(ErrorType.COMMON_INVALID_INPUT.httpStatus).body(body);
    }

    /** 그 외 예상 못한 모든 예외 → 500으로 통일 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleEtc(Exception e, HttpServletRequest req) {
        log.error("Unexpected error: path={}, msg={}", req.getRequestURI(), e.getMessage(), e);
        ApiErrorResponse body = ApiErrorResponse.of(ErrorType.COMMON_INTERNAL, null, req.getRequestURI(), requestId(req), null);
        return ResponseEntity.status(ErrorType.COMMON_INTERNAL.httpStatus).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleRese(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());

        ErrorType type = switch (status) {
            case NOT_FOUND -> ErrorType.COMMON_NOT_FOUND;
            case BAD_REQUEST -> ErrorType.COMMON_INVALID_INPUT;
            case FORBIDDEN -> ErrorType.COMMON_FORBIDDEN;
            default -> ErrorType.COMMON_INTERNAL;
        };

        ApiErrorResponse body = ApiErrorResponse.of(
            type,
            e.getReason(),
            req.getRequestURI(),
            requestId(req),
            null
        );

        return ResponseEntity.status(status).body(body);
    }

    private static Object safeRejectedValue(FieldError fe) {
        Object v = fe.getRejectedValue();
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.length() > 200 ? s.substring(0, 200) + "..." : v;
    }

    /** 요청 추적용 ID(필요하면 필터에서 생성/주입, 지금은 헤더만 읽음) */
    private static String requestId(HttpServletRequest req) {
        String h = req.getHeader("X-Request-Id");
        return (h != null && !h.isBlank()) ? h : null;
    }
}