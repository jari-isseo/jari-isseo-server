package com.jariisseo.status;

import com.jariisseo.auth.JwtTokenService;
import com.jariisseo.global.config.SwaggerConfig;
import com.jariisseo.global.config.SwaggerTags;
import com.jariisseo.status.dto.StatusSnapshotResponse;
import com.jariisseo.status.dto.TableStatusResponse;
import com.jariisseo.status.dto.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 사장님 - 테이블 상태 (API 정의서 4장)
@Tag(name = SwaggerTags.OWNER_TABLE_STATUS)
@SecurityRequirement(name = SwaggerConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/owner/tables")
public class OwnerTableStatusController {

    private final TableStatusService tableStatusService;

    public OwnerTableStatusController(TableStatusService tableStatusService) {
        this.tableStatusService = tableStatusService;
    }

    @Operation(summary = "테이블 상태 전환",
            description = "토글이 아니라 바꿀 값을 명시 (같은 요청을 두 번 보내도 결과가 같음). 없는 테이블이면 404 TABLE_NOT_FOUND")
    @PutMapping("/{tableId}/status")
    public TableStatusResponse changeStatus(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable String tableId,
                                            @Valid @RequestBody UpdateStatusRequest request) {
        return tableStatusService.changeStatus(JwtTokenService.storeId(jwt), tableId, request.occupied());
    }

    @Operation(summary = "전체 비우기", description = "모든 테이블을 손님 없음으로. 응답은 손님 스냅샷의 statuses 와 같은 형식")
    @PostMapping("/status/reset")
    public StatusSnapshotResponse resetAll(@AuthenticationPrincipal Jwt jwt) {
        return tableStatusService.resetAll(JwtTokenService.storeId(jwt));
    }
}
