package io.routepickapi.dto.recommendation;

import java.util.List;

public record FilterDecision(
    boolean passed,
    List<String> allowlistMatches,
    List<String> blacklistMatches,
    List<String> keywordHits,
    List<String> ruleFailures
) {
}
