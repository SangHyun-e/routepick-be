package io.routepickapi.repository;

import static io.routepickapi.entity.comment.QComment.comment;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.comment.QComment;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 루트 댓글 페이지
     * - ACTIVE 는 항상 노출
     * - DELETED 라도 ACTIVE 자식이 1개 이상 있으면 노출
     * - 최신순(createdAt desc)
     */

    @Override
    public Page<Comment> findRootsForList(Long postId, Pageable pageable) {
        QComment r = comment;
        QComment ch = new QComment("ch");

        // content 조회
        List<Comment> content = queryFactory
            .selectFrom(r)
            .where(
                r.post.id.eq(postId),
                r.parent.isNull(),
                r.status.eq(CommentStatus.ACTIVE)
                    .or(
                        r.status.eq(CommentStatus.DELETED)
                            .and(
                                JPAExpressions
                                    .selectOne()
                                    .from(ch)
                                    .where(
                                        ch.parent.eq(r),
                                        ch.status.eq(CommentStatus.ACTIVE)
                                    )
                                    .exists()
                            )
                    )
            )
            .orderBy(r.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // count 쿼리 (동일 조건)
        JPAQuery<Long> countQuery = queryFactory
            .select(r.count())
            .from(r)
            .where(
                r.post.id.eq(postId),
                r.parent.isNull(),
                r.status.eq(CommentStatus.ACTIVE)
                    .or(
                        r.status.eq(CommentStatus.DELETED)
                            .and(
                                JPAExpressions
                                    .selectOne()
                                    .from(ch)
                                    .where(
                                        ch.parent.eq(r),
                                        ch.status.eq(CommentStatus.ACTIVE)
                                    )
                                    .exists()
                            )
                    )
            );
        // count 최적화: content 사이즈로 생략 가능하면 생략
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 대댓글 일괄 조회
     * - ACTIVE 만 노출
     * - 작성시간 오름차순(createdAt asc)
     */
    @Override
    public List<Comment> findActiveReplies(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyList();
        }

        QComment c = comment;
        return queryFactory
            .selectFrom(c)
            .where(
                c.parent.id.in(parentIds),
                c.status.eq(CommentStatus.ACTIVE)
            )
            .orderBy(c.createdAt.asc())
            .fetch();
    }
}
