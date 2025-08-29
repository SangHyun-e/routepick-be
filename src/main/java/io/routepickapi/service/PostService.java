package io.routepickapi.service;

import io.routepickapi.dto.post.PostCreateRequest;
import io.routepickapi.dto.post.PostListItemResponse;
import io.routepickapi.dto.post.PostResponse;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.repository.PostRepository;
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
public class PostService {
    private final PostRepository postRepository;

    public Long create(PostCreateRequest req) {
        Post post = new Post(req.title(), req.content());
        if (req.latitude() != null || req.longitude() != null) {
            post.setCoordinates(req.latitude(), req.longitude());
        }

        if (req.region() != null && !req.region().isBlank()) {
            post.setRegion(req.region());
        }
        post.setTags(req.tags());

        Long id = postRepository.save(post).getId();
        log.info("Create Post: id={}, title='{}', region='{}'", id, post.getTitle(), post.getRegion());
        return id;
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> list(String region, Pageable pageable) {
        if (region == null || region.isBlank()) {
            return postRepository
                .findByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, pageable)
                .map(PostListItemResponse::from);
        }
        return postRepository
            .findByRegionAndStatusOrderByCreatedAtDesc(region, PostStatus.ACTIVE, pageable)
            .map(PostListItemResponse::from);
    }

    public PostResponse getDetail(Long id, boolean increaseView) {
        Post post = postRepository.findWithTagsById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,  "post not found"));

        if (post.getStatus() != PostStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available");
        }

        if (increaseView) {
            post.increaseView();
            log.debug("Increase View: id={}, newViewCount={}", id, post.getViewCount());
        }
        return PostResponse.from(post);
    }

    public int like(Long id) {
        Post post = postRepository.findByIdAndStatus(id, PostStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not available"));
        post.increaseLike();
        log.debug("Increase Like: id={}, newLikeCount={}", id, post.getLikeCount());
        return post.getLikeCount();
    }

    public void softDelete(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));
        post.softDelete();
        log.info("Soft Delete: id={}", id);
    }

    public void activate(Long id) {
        Post post = postRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "post not found"));
        post.activated();
        log.info("Activate: id={}", id);
    }
}
