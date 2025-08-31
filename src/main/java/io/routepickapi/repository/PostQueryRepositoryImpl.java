package io.routepickapi.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.routepickapi.entity.post.Post;
import io.routepickapi.entity.post.PostStatus;
import io.routepickapi.entity.post.QPost;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * QueryDSL 구현부.
 * 동적 조건(BooleanBuilder) + 페이지/정렬 적용 + 카운트 쿼리 분리
 */
@Repository
@RequiredArgsConstructor
public class PostQueryRepositoryImpl implements PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Post> searchByRegionAndKeyword(String region, String keyword, Pageable pageable) {
        // 1) Q타입: 엔티티 Post의 메타클래스(컴파일 시 생성됨)
        // - post.title, post.content, post.createdAt 같은 필드에 안전하게 접근 가능
        QPost post = QPost.post;

        // 2) 동적 where 조건
        BooleanBuilder where = new BooleanBuilder();
        // 2-1) 상태는 ACTIVE만 노출
        where.and(post.status.eq(PostStatus.ACTIVE));
        // 2-2) 지역 필터 (region이 비어있지 않다면)
        if (region != null && !region.isBlank()) {
            where.and(post.region.eq(region));
        }
        // 2-3) 키워드 검색: 제목 or 본문에 'keyword' 포함(대소문자 무시)
        if (keyword != null && !keyword.isBlank()) {
            // 제목 또는 본문에 키워드 포함
            where.and(post.title.containsIgnoreCase(keyword)
                .or(post.content.containsIgnoreCase(keyword)));
        }

        // 3) 정렬: 기본 createdAt DESC (페이지 정렬이 들어오면 우선 적용)
        //         (pageable.getSort()로 들어온 정렬이 있다면 추가로 매핑 가능)
        OrderSpecifier<?> defaultSort = post.createdAt.desc();

        // 4) 본문 조회 쿼리: select * from posts where ... order by ... limit ... offset ...
        JPAQuery<Post> contentQuerry = queryFactory
            .selectFrom(post)               // select post
            .where(where)                   // where 조건들
            .orderBy(defaultSort)           // 기본 정렬
            .offset(pageable.getOffset())   // 페이지 시작 위치
            .limit(pageable.getPageSize()); // 페이지 크기

        // (선택) pageable 정렬 매핑: 필요할 때 확장
        // pageable.getSort().forEach(order -> {
        //     String prop = order.getProperty();
        //     boolean asc = order.isAscending();
        //     if ("createdAt".equals(prop)) {
        //         contentQuery.orderBy(asc ? post.createdAt.asc() : post.createdAt.desc());
        //     } else if ("likeCount".equals(prop)) {
        //         contentQuery.orderBy(asc ? post.likeCount.asc() : post.likeCount.desc());
        //     }
        //     // 필요한 정렬 항목을 여기에 추가
        // });

        List<Post> content = contentQuerry.fetch(); // 실제 조회 실행

        // 5) 카운트 쿼리: total count (페이지네이션 응답을 위해 분리)
        JPAQuery<Long> countQuery = queryFactory
            .select(post.count())
            .from(post)
            .where(where);

        /**
         * fetchOne()은 Long(객체형)을 반환함
         * 정상적으로는 COUNT(*)라서 0 이상 숫자 하나가 오지만, 프레임워크/드라이버에 따라 드물게 null이 올 수 있음(방어 코드)
         * PageImpl 생성자 등에 넘길 때는 원시형 long이 편함
         * 그냥 long total = countQuery.fetchOne(); 하면 null일 때 NPE 터짐
         * 그래서 Long으로 받았다가 널 체크 후 언박싱(.longValue())하는 패턴을 씀
         * */
        Long totalBoxed = countQuery.fetchOne();
        long total = (totalBoxed == null) ? 0L : totalBoxed.longValue();

        // 6) 스프링 데이터의 page 구현체로 감싸서 반환
        Page<Post> page = new PageImpl<>(content, pageable, total);
        return page;
    }
}
