package com.jariisseo.store;

import com.jariisseo.auth.JwtTokenService;
import com.jariisseo.global.config.SwaggerConfig;
import com.jariisseo.global.config.SwaggerTags;
import com.jariisseo.store.dto.MyStoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 사장님 - 매장. 토큰의 매장 id 로 '내 매장'을 찾는다 (URL 에 매장 id 가 없음)
// 매장 수정(PATCH)은 매장 API 이슈에서
@Tag(name = SwaggerTags.OWNER_STORE)
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/owner/store")
public class OwnerStoreController {

    private final StoreService storeService;

    public OwnerStoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @Operation(summary = "내 매장 정보 + 공유 링크", description = "토큰 없음·만료 시 401 UNAUTHORIZED")
    @GetMapping
    public MyStoreResponse myStore(@AuthenticationPrincipal Jwt jwt) {
        return storeService.getMyStore(JwtTokenService.storeId(jwt));
    }
}
