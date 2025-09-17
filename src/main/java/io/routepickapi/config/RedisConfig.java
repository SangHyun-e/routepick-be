package io.routepickapi.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisConfig {

    private final RedisProperties redisProps;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 단일 노드 접속 정보(host/port/password) 구성
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
            redisProps.getHost(),
            redisProps.getPort()
        );
        if (redisProps.getPassword() != null && !redisProps.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(redisProps.getPassword()));
        }

        // 커넥션 타임아웃 등 클라이언트 옵션 설정 (yml의 timeout 사용)
        LettuceClientConfiguration clientCfg = LettuceClientConfiguration.builder()
            .commandTimeout(redisProps.getTimeout())
            .build();

        // Lettuce 기반 커넥션 팩토리 반환 (스프링 데이터 레디스가 내부에서 사용)
        return new LettuceConnectionFactory(standalone, clientCfg);
    }

    @Bean
    public RedisTemplate<String, Object> jsonRedisTemplate(LettuceConnectionFactory cf) {
        // 값에 객체(JSON)를 저장하고 싶을 때 사용 (확장 대비)
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        // Jackson 기반 범용 JSON 직렬화 (타입 정보 포함 X -> 단순 DTO)
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(json);
        template.setHashValueSerializer(json);
        template.afterPropertiesSet();
        return template;
    }

    @PostConstruct
    public void logRedisConn() {
        log.info("Redis connect -> {}:{}, timeout={}", redisProps.getHost(), redisProps.getPort(),
            redisProps.getTimeout());
    }
}
