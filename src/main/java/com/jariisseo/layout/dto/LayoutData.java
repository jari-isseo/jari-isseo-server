package com.jariisseo.layout.dto;

import java.util.List;

// API 정의서 2장 Layout JSON (version 제외). PUT 요청 본문이자, layout.data 에 저장하는 내용
//   { "unit": "m", "floors": [ { "id", "name", "order", "floor": { "polygon" }, "walls", "tables", "chairs" } ] }
// 요청에 version 이 있어도 무시한다 (서버가 정함)
public record LayoutData(String unit, List<FloorData> floors) {

    public record FloorData(
            String id,
            String name,
            Integer order,
            FloorShape floor,
            List<WallData> walls,
            List<TableData> tables,
            List<ChairData> chairs
    ) {
    }

    // 바닥 외곽선: [[x, z], [x, z], ...]
    public record FloorShape(List<double[]> polygon) {
    }

    // 벽 선분: from [x, z] → to [x, z]
    public record WallData(String id, double[] from, double[] to) {
    }

    // x, z 는 테이블 중심. width·depth·rotation·shape 는 비어 있으면 기본값 (0.7, 0.7, 0, RECT)
    public record TableData(
            String id,
            String name,
            Double x,
            Double z,
            Double width,
            Double depth,
            Double rotation,
            String shape
    ) {
    }

    // 장식용 의자 (터치·상태 없음)
    public record ChairData(String id, Double x, Double z) {
    }
}
