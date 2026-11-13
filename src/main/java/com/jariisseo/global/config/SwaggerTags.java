package com.jariisseo.global.config;

/**
 * Swagger 태그 이름. 컨트롤러에서 {@code @Tag(name = SwaggerTags.AUTH)}처럼 사용한다.
 */
public final class SwaggerTags {

    public static final String AUTH = "인증";
    public static final String OWNER_STORE = "사장님 - 매장";
    public static final String OWNER_LAYOUT = "사장님 - 배치";
    public static final String OWNER_TABLE_STATUS = "사장님 - 테이블 상태";
    public static final String PUBLIC = "손님 웹";

    private SwaggerTags() {
    }
}
