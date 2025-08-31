package io.routepickapi.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* QueryDSL에서 JPQL 작성/실행을 도와주는 JPAQueryFactory Bean 등록
* - EntityManager를 주입받아 재사용함(스레드 세이프 관점에서 생성자 주입이 더 유리함)
*/
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        JPAQueryFactory factory = new JPAQueryFactory(entityManager);
        return factory;
    }
}
