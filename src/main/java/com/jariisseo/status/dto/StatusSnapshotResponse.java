package com.jariisseo.status.dto;

import com.jariisseo.status.TableStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

// 전체 비우기 응답: 손님 스냅샷(API 정의서 5장)의 statuses 와 같은 모양 + lastUpdatedAt
//   { "statuses": [ { "tableId": "t-3f9a", "occupied": false, "updatedAt": "..." } ], "lastUpdatedAt": "..." }
//   SSE snapshot 이벤트의 data 와 같은 형식이라, 앱·웹이 같은 코드로 읽을 수 있다
public record StatusSnapshotResponse(List<StatusItem> statuses, Instant lastUpdatedAt) {

    public record StatusItem(String tableId, boolean occupied, Instant updatedAt) {
    }

    public static StatusSnapshotResponse of(List<TableStatus> statuses) {
        List<StatusItem> items = statuses.stream()
                .sorted(Comparator.comparing(TableStatus::getTableId))
                .map(s -> new StatusItem(s.getTableId(), s.isOccupied(), s.getUpdatedAt()))
                .toList();
        Instant last = statuses.stream()
                .map(TableStatus::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);   // 테이블이 하나도 없으면 null
        return new StatusSnapshotResponse(items, last);
    }
}
