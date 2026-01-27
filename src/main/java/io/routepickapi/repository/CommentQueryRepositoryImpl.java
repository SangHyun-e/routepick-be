package io.routepickapi.repository;

import static io.routepickapi.entity.comment.QComment.comment;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.comment.CommentStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommentQueryRepositoryImpl implements CommentQueryRepository {

    private final JPAQueryFactory queryFactory;


    @Override
    public Map<Long, Integer> countByPostIds(List<Long> postIds, CommentStatus status) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = queryFactory
            .select(comment.post.id, comment.id.count())
            .from(comment)
            .where(
                comment.post.id.in(postIds),
                comment.status.eq(status)
            )
            .groupBy(comment.post.id)
            .fetch();

        Map<Long, Integer> result = new HashMap<>();
        for (Tuple row : rows) {
            Long postId = row.get(comment.post.id);
            Long cntLong = row.get(comment.id.count());

            if (postId == null || cntLong == null) {
                continue;
            }
            result.put(postId, Math.toIntExact(cntLong));
        }
        return result;
    }
}
