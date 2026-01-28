package io.routepickapi.repository;

import static io.routepickapi.entity.comment.QComment.comment;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.comment.QComment;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 루트 댓글 조회
     * - parent == null
     * - ACTIVE 는 항상 노출
     * - DELETED 는 자식이 하나라도 있으면 노출
     */
    @Override
    public Page<Comment> findRootsForList(Long postId, Pageable pageable) {

        QComment child = new QComment("child");

        BooleanBuilder where = new BooleanBuilder();
        where.and(comment.post.id.eq(postId));
        where.and(comment.parent.isNull());

        BooleanBuilder statusPolicy = new BooleanBuilder();
        statusPolicy.or(comment.status.eq(CommentStatus.ACTIVE));
        statusPolicy.or(
            comment.status.eq(CommentStatus.DELETED)
                .and(
                    JPAExpressions
                        .selectOne()
                        .from(child)
                        .where(
                            child.parent.id.eq(comment.id),
                            child.post.id.eq(postId),
                            child.status.in(CommentStatus.ACTIVE, CommentStatus.DELETED)
                        )
                        .exists()
                )
        );

        where.and(statusPolicy);

        List<Comment> content = queryFactory
            .selectFrom(comment)
            .leftJoin(comment.author).fetchJoin()
            .where(where)
            .orderBy(comment.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(comment.count())
            .from(comment)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 대댓글 조회
     * - 부모 ID 기준
     * - ACTIVE / DELETED 모두 포함
     */
    @Override
    public List<Comment> findRepliesForList(List<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
            .selectFrom(comment)
            .leftJoin(comment.author).fetchJoin()
            .where(
                comment.parent.id.in(parentIds),
                comment.status.in(CommentStatus.ACTIVE, CommentStatus.DELETED)
            )
            .orderBy(comment.createdAt.asc())
            .fetch();
    }
}