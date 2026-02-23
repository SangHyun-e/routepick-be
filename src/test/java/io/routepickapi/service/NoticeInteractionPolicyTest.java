package io.routepickapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.routepickapi.common.error.CustomException;
import io.routepickapi.common.error.ErrorType;
import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.user.User;
import io.routepickapi.repository.PostRepository;
import io.routepickapi.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoticeInteractionPolicyTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void blocksCommentCreationOnNotice() {
        authenticate();
        User user = createActiveUser("commenter@example.com");
        Post notice = createNoticePost();

        CustomException exception = assertThrows(
            CustomException.class,
            () -> commentService.createRoot(
                notice.getId(),
                user.getId(),
                new CommentCreateRequest("공지 댓글 테스트")
            )
        );

        assertThat(exception.getType()).isEqualTo(ErrorType.POST_NOTICE_COMMENT_NOT_ALLOWED);
    }

    @Test
    void blocksPostLikeOnNotice() {
        authenticate();
        User user = createActiveUser("liker@example.com");
        Post notice = createNoticePost();

        CustomException exception = assertThrows(
            CustomException.class,
            () -> postService.toggleLike(notice.getId(), user.getId())
        );

        assertThat(exception.getType()).isEqualTo(ErrorType.POST_NOTICE_LIKE_NOT_ALLOWED);
    }

    private User createActiveUser(String email) {
        User user = new User(email, "hash", email.split("@")[0]);
        user.activate();
        return userRepository.save(user);
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("test", "N/A", List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Post createNoticePost() {
        Post post = new Post("공지", "notice");
        post.toggleNotice();
        return postRepository.save(post);
    }
}
