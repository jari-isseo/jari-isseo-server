package com.jariisseo.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("자리있어 API")
                        .description("""
                                사장님 앱이 테이블 배치와 상태를 저장하고, 손님 웹이 실시간으로 빈자리를 확인하는 API입니다.

                                - `/api/auth/**`: 가입·로그인 (토큰 불필요)
                                - `/api/owner/**`: 사장님 전용, `Authorization: Bearer {JWT}` 필요
                                - `/api/public/**`: 손님 웹용 공개 API (스냅샷·SSE)
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답으로 받은 토큰을 입력합니다.")))
                .tags(List.of(
                        new Tag().name(SwaggerTags.AUTH)
                                .description("사장님 가입(매장 동시 생성)과 로그인, JWT 발급"),
                        new Tag().name(SwaggerTags.OWNER_STORE)
                                .description("내 매장 정보 조회·수정 (이름, 영업 여부)"),
                        new Tag().name(SwaggerTags.OWNER_LAYOUT)
                                .description("테이블 배치(floors, 바닥, 벽, 테이블·의자) 조회·저장"),
                        new Tag().name(SwaggerTags.OWNER_TABLE_STATUS)
                                .description("테이블 손님 있음·없음 전환과 전체 비우기"),
                        new Tag().name(SwaggerTags.PUBLIC)
                                .description("손님 웹이 쓰는 매장 스냅샷 조회와 SSE 실시간 이벤트 구독")));
    }
}
