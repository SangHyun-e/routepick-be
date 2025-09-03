package io.routepickapi.service;

import io.routepickapi.dto.comment.CommentCreateRequest;
import io.routepickapi.dto.comment.CommentResponse;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.repository.CommentRepository;
import io.routepickapi.repository.PostRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public Long createRoot(Long postId, CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available"));

        Comment comment = Comment.builder()
            .post(post)
            .parent(null)
            .content(req.content())
            .build();

        Long id = commentRepository.save(comment).getId();
        log.info("Create root comment: postId={}, commentId={}", postId, id);
        return id;
    }

    public Long createReply(Long postId, Long parentId, CommentCreateRequest req) {
        Post post = postRepository.findByIdAndStatus(postId, PostStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available"));

        Comment parent = commentRepository.findById(parentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "parent comment not found"));

        Comment reply = Comment.builder()
            .post(post)
            .parent(parent)
            .content(req.content())
            .build();

        Long id = commentRepository.save(reply).getId();
        log.info("Create reply: postId={}, parentId={}, commentId={}", postId, parentId, id);
        return id;
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> listRootsWithReplies(Long postId, Pageable pageable) {
        // 1) 본댓글 페이지(최신순)
        Page<Comment> roots = commentRepository
            .findByPostIdAndParentIsNullAndStatusOrderByCreatedAtDesc(
                postId, CommentStatus.ACTIVE, pageable
            );

        // 2) 본댓글 ID 수집
        List<Long> parentIds = roots.getContent().stream()
            .map(Comment::getId)
            .toList();

        // 3) 대댓글 일괄 조회(작성시간 오름차순)
        List<Comment> replies = parentIds.isEmpty()
            ? List.of()
            : commentRepository.findByParentIdInAndStatusOrderByCreatedAtAsc(
                parentIds, CommentStatus.ACTIVE
            );

        // 4) parentId -> children 맵핑
        Map<Long, List<Comment>> childrenMap = replies.stream()
            .collect(Collectors.groupingBy(c-> c.getParent().getId()));

        // 5) DTO 변환 (root + children)
        return roots.map(root ->
            CommentResponse.fromWithChildren(
                root,
                childrenMap.getOrDefault(root.getId(), List.of())
            )
        );
    }
}
