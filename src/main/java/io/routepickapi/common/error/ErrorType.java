package io.routepickapi.common.error;

import org.springframework.http.HttpStatus;

/*
 *  API 에서 공통으로 쓰는 에러
 * - httpStatus: HTTP 응답 코드
 * - code: FE/로그/문서에서 보는 짧은 식별자
 * - message: 기본 메세지(상세는 예외에서 덮어쓸 수 있음)
 */
public enum ErrorType {

    /* === 공통 === */
    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "CMN-001", "유효하지 않은 입력입니다."),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "CMN-401", "인증이 필요합니다."),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "CMN-003", "권한이 없습니다."),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "CMN-404", "대상을 찾을 수 없습니다."),
    COMMON_CONFLICT(HttpStatus.CONFLICT, "CMN-409", "이미 존재하거나 충돌이 발생했습니다."),
    COMMON_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "CMN-429", "요청이 너무 많습니다."),
    COMMON_INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "CMN-500", "서버 오류가 발생했습니다."),

    /* === 인증/토큰 === */
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-401", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-402", "유효하지 않은 토큰입니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-403", "만료된 토큰입니다."),
    AUTH_OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH-405", "OAuth 인증에 실패했습니다."),
    AUTH_OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH-406", "카카오 이메일 인증이 필요합니다."),
    AUTH_OAUTH_UNLINK_FAILED(HttpStatus.BAD_GATEWAY, "AUTH-407", "카카오 연결 해제에 실패했습니다."),

    /* === 이메일 인증 코드 === */
    AUTH_EMAIL_VERIFY_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH-410", "인증코드가 올바르지 않습니다."),
    AUTH_EMAIL_VERIFY_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH-411", "인증코드가 만료되었거나 존재하지 않습니다."),
    AUTH_EMAIL_VERIFY_TOO_MANY_TRIES(HttpStatus.TOO_MANY_REQUESTS, "AUTH-429",
        "인증 시도 횟수를 초과했습니다. 다시 발급받아주세요."),

    /* === 비밀번호 재설정 코드 === */
    AUTH_PASSWORD_RESET_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH-420",
        "비밀번호 재설정 코드가 올바르지 않습니다."),
    AUTH_PASSWORD_RESET_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH-421",
        "비밀번호 재설정 코드가 만료되었거나 존재하지 않습니다."),
    AUTH_PASSWORD_RESET_TOO_MANY_TRIES(HttpStatus.TOO_MANY_REQUESTS, "AUTH-430",
        "비밀번호 재설정 시도 횟수를 초과했습니다. 다시 요청해주세요."),

    /* === 유저 === */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-404", "사용자를 찾을 수 없습니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "USER-403", "차단된 사용자입니다."),
    USER_STATUS_INACTIVE(HttpStatus.FORBIDDEN, "USER-405", "비활성 상태의 사용자입니다."),
    USER_EMAIL_EXISTS(HttpStatus.CONFLICT, "USER-409", "이미 사용 중인 이메일입니다."),
    USER_NICKNAME_EXISTS(HttpStatus.CONFLICT, "USER-410", "이미 사용 중인 닉네임입니다."),
    USER_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "USER-412",
        "이메일 인증이 필요합니다. 마이페이지에서 인증을 완료해주세요."),

    USER_PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "USER-415", "비밀번호가 설정되지 않은 계정입니다."),

    USER_REJOIN_RESTRICTED(HttpStatus.FORBIDDEN, "USER-414", "탈퇴 후 재가입 제한 상태입니다."),

    USER_STATUS_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "USER-413", "상태 변경이 허용되지 않습니다."),

    USER_EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "USER-409", "이미 인증이 완료된 이메일입니다."),

    /* === 게시글 === */
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST-404", "게시글을 찾을 수 없습니다."),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "POST-403", "게시글에 대한 권한이 없습니다."),
    POST_NOTICE_COMMENT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "POST-405",
        "공지글에는 댓글을 작성할 수 없습니다."),
    POST_NOTICE_LIKE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "POST-406",
        "공지글에는 좋아요를 누를 수 없습니다."),

    /* === 댓글 === */
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CMT-404", "댓글을 찾을 수 없습니다."),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "CMT-403", "댓글에 대한 권한이 없습니다."),
    COMMENT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "CMT-400", "비활성/삭제된 댓글입니다."),
    COMMENT_PARENT_MISMATCH(HttpStatus.BAD_REQUEST, "CMT-401", "부모 댓글이 게시글과 일치하지 않습니다."),
    COMMENT_PARENT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "CMT-402", "부모 댓글이 활성 상태가 아닙니다.");

    public final HttpStatus httpStatus;
    public final String code;
    public final String message;

    ErrorType(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
