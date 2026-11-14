package com.jariisseo.customer;

import com.jariisseo.customer.dto.SnapshotResponse;
import com.jariisseo.global.config.SwaggerTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 손님(공개) API — 인증 없음 (API 정의서 5장)
@Tag(name = SwaggerTags.PUBLIC)
@RestController
@RequestMapping("/api/public/stores")
public class PublicStoreController {

    private final CustomerSnapshotService snapshotService;

    public PublicStoreController(CustomerSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Operation(summary = "매장 스냅샷 (배치 + 상태)",
            description = "배치가 아직 없으면 layout: null. 없는 publicId 면 404 STORE_NOT_FOUND")
    @GetMapping("/{publicId}")
    public SnapshotResponse snapshot(@PathVariable String publicId) {
        return snapshotService.getSnapshot(publicId);
    }
}
