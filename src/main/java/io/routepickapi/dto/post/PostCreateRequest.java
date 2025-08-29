package io.routepickapi.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "게시글 생성 요청")
public record PostCreateRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 4000) String content,
    Double latitude,
    Double longitude,
    @Size(max = 120) String region,
    List<String> tags
) {}
