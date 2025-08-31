package io.routepickapi.repository;

import io.routepickapi.entity.post.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
* QueryDSL 기반 복합/동적 조회용 인터페이스
* - 예시: region(선택), 키워드(선택)로 최신순 페이지 조회
*/
public interface PostQueryRepository {
    Page<Post> searchByRegionAndKeyword(String region, String keyword, Pageable pageable);
}
