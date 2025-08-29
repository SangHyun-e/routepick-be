package io.routepickapi.common.error;

import org.springframework.http.HttpStatus;

/*
* API에서 공통으로 쓰는 에러
* - httpStatus: HTTP 응답 코드
* - code: FE/로그/문서에서 보는 짧은 식별자
* - message: 기본 메세지(상세는 예외에서 덮어쓸 수 있음)
*/
public enum ErrorType {
    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "CMN-001", "유효하지 않은 입력입니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "CMN-404", "대상을 찾을 수 없습니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "CMN-003", "권한이 없습니다."),
    COMMON_INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "CMN-500", "서버 오류가 발생했습니다."),

    // 도메인 별
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST-404", "게시글을 찾을 수 없습니다.");

    public final HttpStatus httpStatus;
    public final String code;
    public final String message;

    ErrorType(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
