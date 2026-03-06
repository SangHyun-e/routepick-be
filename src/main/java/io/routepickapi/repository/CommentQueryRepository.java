package io.routepickapi.repository;

import io.routepickapi.entity.comment.CommentStatus;
import java.util.List;
import java.util.Map;

public interface CommentQueryRepository {

    Map<Long, Integer> countByPostIds(List<Long> postIds, CommentStatus status);
}
