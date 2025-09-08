package io.routepickapi.security;

import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * @PreAuthorize 표현식에서 쓰는 권한 체크 헬퍼
 * - @Component("authz") 로 등록해 SpEL 에서 @authz 로 접근
 */
@Component("authz")
@RequiredArgsConstructor
public class Authz {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    // 현재 인증된 사용자 ID 추출 (없으면 null)
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthUser user) {
            return user.id();
        }
        return null;
    }

    // 게시글 소유자(작성자)인지 판단
    public boolean isPostOwner(Long postId) {
        Long userId = currentUserId();
        return userId != null && postRepository.existsByIdAndAuthorId(postId, userId);
    }

    // 댓글 소유자(작성자)인지 판단 + postId 까지 확인
    public boolean isCommentOwner(Long postId, Long commentId) {
        Long userId = currentUserId();
        return userId != null && commentRepository.existsByIdAndPostIdAndAuthorId(commentId, postId,
            userId);
    }
}
