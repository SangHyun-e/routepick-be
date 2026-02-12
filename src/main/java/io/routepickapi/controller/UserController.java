package io.routepickapi.controller;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.MyCommentResponse;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.user.MeResponse;
import io.routepickapi.dto.user.PasswordVerifyRequest;
import io.routepickapi.security.AuthUser;
import io.routepickapi.service.AuthService;
import io.routepickapi.service.UserActivityService;
import io.routepickapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserActivityService userActivityService;

    /**
     * 내 정보 조회
     * - SecurityConfig 에서 인증 필요로 보호 JwtAuthenticationFilter 가 SecurityContext 에 AuthUser 주입
     */
    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 프로필 반환",
        security = {@SecurityRequirement(name = "bearerAuth")} // OpenAPI: Bearer 필요
    )
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser currentUser) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        log.info("GET /users/me - userId={}", currentUser.id());
        return userService.getMe(currentUser.id());
    }

    @Operation(
        summary = "내가 쓴 글 조회",
        description = "현재 로그인한 사용자의 게시글 목록 반환",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @GetMapping("/me/posts")
    public Page<PostListItemResponse> myPosts(
        @AuthenticationPrincipal AuthUser currentUser,
        @ParameterObject
        @PageableDefault(size = 3, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable,
        @RequestParam(name = "status", defaultValue = "ALL") String status
    ) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        log.info("GET /users/me/posts - userId={}", currentUser.id());
        return userActivityService.getMyPosts(currentUser.id(), pageable, status);
    }

    @Operation(
        summary = "내가 쓴 댓글 조회",
        description = "현재 로그인한 사용자의 댓글 목록 반환",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @GetMapping("/me/comments")
    public Page<MyCommentResponse> myComments(
        @AuthenticationPrincipal AuthUser currentUser,
        @ParameterObject
        @PageableDefault(size = 3, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        log.info("GET /users/me/comments - userId={}", currentUser.id());
        return userActivityService.getMyComments(currentUser.id(), pageable);
    }

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 로그인 사용자를 소프트 삭제 처리",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
        @AuthenticationPrincipal AuthUser currentUser,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
    ) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        log.info("DELETE /users/me - userId={}", currentUser.id());
        userService.withdraw(currentUser.id());
        authService.logoutAll(authHeader, currentUser.id());

        ResponseCookie clear = ResponseCookie.from("RP_REFRESH", "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, clear.toString())
            .build();
    }

    @Operation(
        summary = "비밀번호 확인",
        description = "회원 탈퇴 전 비밀번호 확인",
        security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @PostMapping({"/me/verify-password", "/me/verify-password/"})
    public ResponseEntity<Void> verifyPassword(
        @AuthenticationPrincipal AuthUser currentUser,
        @Valid @RequestBody PasswordVerifyRequest req
    ) {
        if (currentUser == null) {
            throw new CustomException(ErrorType.COMMON_UNAUTHORIZED);
        }
        userService.verifyPassword(currentUser.id(), req.password());
        return ResponseEntity.noContent().build();
    }
}
