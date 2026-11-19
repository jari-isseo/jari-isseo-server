package com.jariisseo.status.dto;

import jakarta.validation.constraints.NotNull;

// PUT /api/owner/tables/{tableId}/status 요청: { "occupied": true }
//   토글이 아니라 '바꿀 값'을 보낸다 → 같은 요청을 두 번 보내도 결과가 같음 (API 정의서 4장)
public record UpdateStatusRequest(

        @NotNull(message = "occupied 값을 보내 주세요")
        Boolean occupied
) {
}
