package io.routepickapi.repository;

import static io.routepickapi.entity.comment.QComment.comment;
import static io.routepickapi.entity.post.QPost.post;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.comment.CommentStatus;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

/**
 * QueryDSL 구현부. 동적 조건(BooleanBuilder) + 페이지/정렬 적용 + 카운트 쿼리 분리
 */
@Repository
@RequiredArgsConstructor
public class PostQueryRepositoryImpl implements PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> searchByRegionAndKeyword(String region, String keyword, Pageable pageable) {
        return searchByStatusRegionAndKeyword(PostStatus.ACTIVE, region, keyword, pageable);
    }

    @Override
    public Page<Post> searchByStatusRegionAndKeyword(
        PostStatus status,
        String region,
        String keyword,
        Pageable pageable
    ) {
        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(post.status.eq(status));
        }
        if (region != null && !region.isBlank()) {
            where.and(post.region.eq(region));
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            where.and(post.title.containsIgnoreCase(k)
                .or(post.content.containsIgnoreCase(k)));
        }

        boolean hasCommentCountSort = pageable.getSort().stream()
            .anyMatch(o -> "commentCount".equals(o.getProperty()));

        NumberExpression<Long> commentCountExpr = comment.id.countDistinct();

        JPAQuery<Post> contentQuery = queryFactory
            .selectFrom(post)
            .where(where);

        if (hasCommentCountSort) {
            contentQuery
                .leftJoin(comment)
                .on(
                    comment.post.id.eq(post.id),
                    comment.status.eq(CommentStatus.ACTIVE)
                )
                .groupBy(post.id);
        }

        List<Post> content = contentQuery
            .orderBy(orderSpecifiers(pageable, commentCountExpr))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(post.count())
            .from(post)
            .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 정렬 화이트리스트 매핑
     * - 허용: createdAt, likeCount, viewCount -미지정/빈 정렬이면 createdAt DESC 기본 적용
     */
    private OrderSpecifier<?>[] orderSpecifiers(Pageable pageable,
        NumberExpression<Long> commentCountExpr) {
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            String prop = order.getProperty();
            boolean asc = order.isAscending();

            if ("createdAt".equals(prop)) {
                orders.add(asc ? post.createdAt.asc() : post.createdAt.desc());
            } else if ("likeCount".equals(prop)) {
                orders.add(asc ? post.likeCount.asc() : post.likeCount.desc());
            } else if ("viewCount".equals(prop)) {
                orders.add(asc ? post.viewCount.asc() : post.viewCount.desc());
            } else if ("commentCount".equals(prop)) {
                orders.add(asc ? commentCountExpr.asc() : commentCountExpr.desc());
            }
        });

        if (orders.isEmpty()) {
            orders.add(post.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
