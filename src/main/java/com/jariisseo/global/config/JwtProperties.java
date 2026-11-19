package com.jariisseo.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// application.properties 의 jwt.* 값
//   jwt.secret     = 서명 비밀키 (.env 의 JWT_SECRET)
//   jwt.expires-in = 토큰 유효기간 (30d)
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration expiresIn) {
}
