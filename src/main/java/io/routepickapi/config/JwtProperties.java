package io.routepickapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    // HMAC 서명용 비밀키 ( Base64로 인코딩된 문자열 )
    private String secret;

    // 액세스 토큰 만료(초 단위)
    private long accessTtlSeconds = 3600;

    private String issuer = "routepick";


}
