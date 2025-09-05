package io.routepickapi.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "게시글 생성 요청")
public record PostCreateRequest(
    @NotBlank @Size(max = 120) String title,
    @NotBlank @Size(max = 4000) String content,

    @Size(max = 120)
    @Pattern(regexp = ".*\\S.*", message = "region은 공백만으로는 허용되지 않습니다.")
    String region,

    Double latitude,
    Double longitude,

    @Size(max = 50)
    List<@Pattern(regexp = ".*\\S.*", message = "태그는 공백만으로는 허용되지 않습니다.") @Size(max = 40) String> tags
) {

    @AssertTrue(message = "latitude/longitude 둘 다 함께 제공되어야 합니다.")
    public boolean isValidCoordinates() {
        return (latitude == null && longitude == null) || (latitude != null && longitude != null);
    }
}
