package io.routepickapi.repository;

import static io.routepickapi.entity.post.QPost.post;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
        BooleanBuilder where = new BooleanBuilder();
        // 상태는 ACTIVE 만 노출
        where.and(post.status.eq(PostStatus.ACTIVE));
        // 지역 필터 (region 이 비어있지 않다면)
        if (region != null && !region.isBlank()) {
            where.and(post.region.eq(region));
        }
        // 키워드 검색: 제목 or 본문에 'keyword' 포함(대소문자 무시)
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            where.and(post.title.containsIgnoreCase(k)
                .or(post.content.containsIgnoreCase(k)));
        }

        // 본문 쿼리
        List<Post> content = queryFactory
            .selectFrom(post)
            .where(where)
            .orderBy(orderSpecifiers(pageable)) // 정렬 매핑
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        // 카운트 쿼리
        JPAQuery<Long> countQuery = queryFactory
            .select(post.count())
            .from(post)
            .where(where);

        // PageableExecutionUtils: 마지막 페이지 등에서 카운트 생략 최적화
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    /**
     * 정렬 화이트리스트 매핑
     * - 허용: createdAt, likeCount, viewCount -미지정/빈 정렬이면 createdAt DESC 기본 적용
     */
    private OrderSpecifier<?>[] orderSpecifiers(Pageable pageable) {
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
            }
        });

        if (orders.isEmpty()) {
            orders.add(post.createdAt.desc());
        }
        return orders.toArray(new OrderSpecifier<?>[0]);
    }
}
