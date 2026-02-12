package io.routepickapi.repository;

import static io.routepickapi.entity.comment.QComment.comment;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.comment.Comment;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.user.QUser;
import java.util.ArrayList;
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
     * - ACTIVE/DELETED 모두 노출
     */
    @Override
    public Page<Comment> findRootsForList(Long postId, Pageable pageable) {

        BooleanBuilder where = new BooleanBuilder();
        where.and(comment.post.id.eq(postId));
        where.and(comment.parent.isNull());

        BooleanBuilder statusPolicy = new BooleanBuilder();
        statusPolicy.or(comment.status.eq(CommentStatus.ACTIVE));
        statusPolicy.or(comment.status.eq(CommentStatus.DELETED));

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

    @Override
    public List<Comment> findBestComments(Long postId, int minLikes, int limit) {
        if (postId == null) {
            return List.of();

        }
        if (limit <= 0) {
            return List.of();
        }

        int safeMinLikes = Math.max(0, minLikes);

        return queryFactory
            .selectFrom(comment)
            .leftJoin(comment.author).fetchJoin()
            .where(
                comment.post.id.eq(postId),
                comment.status.eq(CommentStatus.ACTIVE),
                comment.likeCount.goe(safeMinLikes)
            )
            .orderBy(
                comment.likeCount.desc(),
                comment.createdAt.desc()
            )
            .limit(limit)
            .fetch();
    }

    @Override
    public Page<Comment> findForAdmin(List<CommentStatus> statuses, String keyword,
        Pageable pageable) {
        BooleanBuilder where = new BooleanBuilder();
        QUser author = new QUser("commentAuthor");

        if (statuses != null && !statuses.isEmpty()) {
            where.and(comment.status.in(statuses));
        }

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            where.and(
                comment.content.containsIgnoreCase(k)
                    .or(comment.post.title.containsIgnoreCase(k))
                    .or(author.nickname.containsIgnoreCase(k))
            );
        }

        JPAQuery<Comment> contentQuery = queryFactory
            .selectFrom(comment)
            .leftJoin(comment.post).fetchJoin()
            .leftJoin(comment.author, author).fetchJoin()
            .leftJoin(comment.parent).fetchJoin()
            .where(where)
            .orderBy(comment.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize());

        List<Comment> content = new ArrayList<>(contentQuery.fetch());

        JPAQuery<Long> countQuery = queryFactory
            .select(comment.count())
            .from(comment)
            .leftJoin(comment.author, author)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
