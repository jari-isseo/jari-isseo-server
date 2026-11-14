package com.jariisseo.store.dto;

// GET /api/owner/store 응답 (API 정의서 4장)
//   QR 코드는 앱이 shareUrl 로 직접 만든다
public record MyStoreResponse(
        String publicId,
        String name,
        boolean open,
        String shareUrl,
        boolean hasLayout
) {
}
