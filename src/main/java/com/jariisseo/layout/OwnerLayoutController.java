package com.jariisseo.layout;

import com.jariisseo.auth.JwtTokenService;
import com.jariisseo.global.config.SwaggerConfig;
import com.jariisseo.global.config.SwaggerTags;
import com.jariisseo.layout.dto.LayoutData;
import com.jariisseo.layout.dto.LayoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 사장님 - 배치 (API 정의서 2장·4장)
@Tag(name = SwaggerTags.OWNER_LAYOUT)
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/owner/store/layout")
public class OwnerLayoutController {

    private final LayoutService layoutService;

    public OwnerLayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @Operation(summary = "현재 배치 조회 (보정 화면 진입 시)",
            description = "저장한 적이 없으면 404 LAYOUT_NOT_FOUND (먼저 GET /api/owner/store 의 hasLayout 으로 확인 가능)")
    @GetMapping
    public LayoutResponse myLayout(@AuthenticationPrincipal Jwt jwt) {
        return layoutService.getMyLayout(JwtTokenService.storeId(jwt));
    }

    @Operation(summary = "배치 저장 (스캔·보정 완료)",
            description = """
                    항상 모든 층을 포함한 배치 전체를 보낸다. 요청의 version 은 무시하고 서버가 +1.
                    같은 테이블 id 는 상태 유지, 새 id 는 손님 없음으로 시작, 사라진 id 는 상태 삭제.
                    검증 규칙 위반 시 400 INVALID_LAYOUT (message 에 이유)""")
    @PutMapping
    public LayoutResponse saveMyLayout(@AuthenticationPrincipal Jwt jwt, @RequestBody LayoutData request) {
        return layoutService.saveMyLayout(JwtTokenService.storeId(jwt), request);
    }
}
