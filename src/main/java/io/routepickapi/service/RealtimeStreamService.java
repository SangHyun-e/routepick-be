package io.routepickapi.service;

import io.routepickapi.dto.realtime.CommentStreamEvent;
import io.routepickapi.dto.realtime.PostStreamEvent;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.user.User;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class RealtimeStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 60L * 60L * 1000L;

    private final List<SseEmitter> postEmitters = new CopyOnWriteArrayList<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> commentEmitters =
        new ConcurrentHashMap<>();

    public SseEmitter subscribePosts() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        postEmitters.add(emitter);
        registerEmitterLifecycle(emitter, () -> postEmitters.remove(emitter));
        sendConnected(emitter);
        return emitter;
    }

    public SseEmitter subscribePostComments(Long postId) {
        CopyOnWriteArrayList<SseEmitter> emitters = commentEmitters.computeIfAbsent(
            postId,
            key -> new CopyOnWriteArrayList<>()
        );
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        emitters.add(emitter);
        registerEmitterLifecycle(emitter, () -> removeCommentEmitter(postId, emitter));
        sendConnected(emitter);
        return emitter;
    }

    public void publishNewPost(Post post) {
        if (post == null) {
            return;
        }

        User author = post.getAuthor();
        PostStreamEvent payload = new PostStreamEvent(
            post.getId(),
            post.getTitle(),
            author != null ? author.getId() : null,
            author != null ? author.getNickname() : null,
            post.getCreatedAt()
        );

        broadcast(postEmitters, "new-post", payload);
    }

    public void publishNewComment(Post post, Comment comment) {
        if (post == null || comment == null) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> emitters = commentEmitters.get(post.getId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        User author = comment.getAuthor();
        CommentStreamEvent payload = new CommentStreamEvent(
            post.getId(),
            comment.getId(),
            author != null ? author.getId() : null,
            author != null ? author.getNickname() : null,
            comment.getCreatedAt()
        );

        broadcast(emitters, "new-comment", payload);
    }

    private void registerEmitterLifecycle(SseEmitter emitter, Runnable removeAction) {
        emitter.onCompletion(removeAction);
        emitter.onTimeout(removeAction);
        emitter.onError((ex) -> removeAction.run());
    }

    private void removeCommentEmitter(Long postId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = commentEmitters.get(postId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            commentEmitters.remove(postId);
        }
    }

    private void sendConnected(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private void broadcast(List<SseEmitter> emitters, String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception ex) {
                emitter.complete();
                emitters.remove(emitter);
                log.debug("SSE emitter removed: event={}, reason={}", eventName,
                    ex.getMessage());
            }
        }
    }
}
