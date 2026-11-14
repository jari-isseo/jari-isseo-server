package com.jariisseo.customer.dto;

import com.jariisseo.layout.dto.LayoutResponse;
import com.jariisseo.status.dto.StatusSnapshotResponse.StatusItem;

import java.time.Instant;
import java.util.List;

// GET /api/public/stores/{publicId} 응답 (API 정의서 5장) — 손님 화면을 처음 그릴 때 필요한 것 전부
//   {
//     "store": { "publicId", "name", "open" },
//     "layout": { "version", "unit", "floors": [...] } 또는 null (배치를 아직 저장하지 않음),
//     "statuses": [ { "tableId", "occupied", "updatedAt" } ],
//     "lastUpdatedAt": 마지막 상태 변경 시각 (상태가 없으면 null)
//   }
public record SnapshotResponse(
        StoreSummary store,
        LayoutResponse layout,
        List<StatusItem> statuses,
        Instant lastUpdatedAt
) {

    public record StoreSummary(String publicId, String name, boolean open) {
    }
}
