package io.routepickapi.dto.recommendation;

import java.util.List;

public record ValidationResult(
    boolean valid,
    List<String> warnings
) {
}
