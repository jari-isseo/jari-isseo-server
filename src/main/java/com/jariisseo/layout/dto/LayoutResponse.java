package com.jariisseo.layout.dto;

import java.util.List;

// GET·PUT /api/owner/store/layout 응답: 저장된 Layout JSON + version (API 정의서 2장·4장)
public record LayoutResponse(int version, String unit, List<LayoutData.FloorData> floors) {
}
