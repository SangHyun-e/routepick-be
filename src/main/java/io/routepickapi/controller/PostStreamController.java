package io.routepickapi.controller;

import io.routepickapi.service.RealtimeStreamService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/posts")
public class PostStreamController {

    private final RealtimeStreamService realtimeStreamService;

    @Operation(summary = "게시글 실시간 스트림", description = "새 글 알림을 SSE로 전달합니다.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPosts() {
        return realtimeStreamService.subscribePosts();
    }

    @Operation(summary = "댓글 실시간 스트림", description = "게시글 댓글 알림을 SSE로 전달합니다.")
    @GetMapping(value = "/{id:\\d+}/comments/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPostComments(@PathVariable(name = "id") @Min(1) Long id) {
        return realtimeStreamService.subscribePostComments(id);
    }
}
