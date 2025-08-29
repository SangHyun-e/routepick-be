package io.routepickapi.common.error;


import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/*
 * FE/로그/문서에서 동일하게 보는 표준 에러 바디
 * 필요없는 필드는 NULL이면 내려주지 않음(JsonInclude)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    LocalDateTime timestamp, // 응답 시각
    int status, // HTTP status code(숫자)
    String code, // 비즈니스 에러 코드
    String message, // 에러 메세지(기본/상세)
    String path, // 요청 경로
    String requestId, // 추적용(있으면)
    List<ApiFieldError> errors // 필드 검증 상세(유효성 실패 시)
) {
    public static ApiErrorResponse of(
        ErrorType type,
        String message,
        String path,
        String requestId,
        List<ApiFieldError> errors
    ) {
        return new ApiErrorResponse(
            LocalDateTime.now(),
            type.httpStatus.value(),
            type.code,
            message != null ? message : type.message,
            path,
            requestId,
            errors
        );
    }

    // 단일 필드 검증 오류 표현
    public record ApiFieldError(String field, String reason, Object rejectedValue) { }
}
