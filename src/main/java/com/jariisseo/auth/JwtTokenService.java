package com.jariisseo.auth;

import com.jariisseo.global.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

// 토큰 발급과, 토큰에서 매장 id 꺼내기
//   토큰 안의 sub(subject) = 매장 id. 매장당 계정 1개라 매장 id 로 사장님을 구분한다 (API 정의서 1장)
@Component
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public String issue(Long storeId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(storeId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiresIn()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // 로그인 응답의 expiresIn (초). 30일 = 2592000
    public long expiresInSeconds() {
        return properties.expiresIn().toSeconds();
    }

    // 컨트롤러에서: myStore(@AuthenticationPrincipal Jwt jwt) → JwtTokenService.storeId(jwt)
    public static Long storeId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
