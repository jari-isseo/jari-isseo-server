package com.jariisseo.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    // 로그인 실패 응답도 GlobalExceptionHandler 가 만든 { code, message } 형식으로 내려가게 한다.
    private final HandlerExceptionResolver exceptionResolver;

    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    // JWT 인증은 #12에서 추가한다.
    // 사장님 API(/api/owner/**)만 로그인 필요, 나머지(손님 웹·공개 API·Swagger·없는 경로)는 열어 둔다.
    //   → 없는 경로는 401 이 아니라 404 NOT_FOUND 로 응답된다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/owner/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e)));
        return http.build();
    }
}
