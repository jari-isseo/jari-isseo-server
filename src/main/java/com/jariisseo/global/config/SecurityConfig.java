package com.jariisseo.global.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

    // 인증 실패 응답도 GlobalExceptionHandler 가 만든 { code, message } 형식으로 내려가게 한다.
    private final HandlerExceptionResolver exceptionResolver;

    public SecurityConfig(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    // API 정의서 1장: 사장님 API(/api/owner/**)만 JWT 필요, 나머지는 인증 없음
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, e) ->
                exceptionResolver.resolveException(request, response, null, e);

        http
                .csrf(csrf -> csrf.disable())
                // 로그인 상태를 세션에 저장하지 않음. 매 요청마다 토큰으로 확인
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE 같은 비동기 응답(ASYNC)·에러 처리(ERROR) 단계에서 다시 인증 검사로 막히지 않게
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/owner/**").authenticated()
                        // 인증 없음: 가입·로그인, 손님 공개 API, 손님 웹 페이지
                        .requestMatchers("/api/auth/**", "/api/public/**", "/s/**", "/viewer/**").permitAll()
                        // 개발 도구: Swagger, dev 샘플 JSON
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/docs/samples/**").permitAll()
                        // 그 밖의 주소도 열어둠 → 없는 주소는 401 이 아니라 404 NOT_FOUND 로 응답 (#10)
                        .anyRequest().permitAll())
                // Authorization: Bearer {토큰} 을 JwtConfig 의 jwtDecoder 로 확인
                //   토큰 없음·서명 틀림·만료 → 401 UNAUTHORIZED
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    // 비밀번호는 BCrypt 해시로만 저장 (API 정의서 3장)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
