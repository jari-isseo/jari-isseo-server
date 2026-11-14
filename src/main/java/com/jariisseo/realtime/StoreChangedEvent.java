package com.jariisseo.realtime;

import com.jariisseo.status.dto.StatusSnapshotResponse;
import com.jariisseo.status.dto.StatusSnapshotResponse.StatusItem;

// 사장님 변경 → 손님 화면에 보낼 SSE 이벤트 (API 정의서 5장)
//   서비스가 트랜잭션 안에서 이 이벤트를 발행하면, StoreChangedListener 가 커밋된 뒤에 손님들에게 보낸다
//
//   | name     | data                                   | 언제          |
//   | status   | { tableId, occupied, updatedAt }       | 테이블 1개 변경 |
//   | snapshot | { statuses, lastUpdatedAt }            | 전체 비우기     |
//   | layout   | { version }                            | 배치 재저장     |
//   | store    | { name, open }                         | 매장 정보 변경  |
public record StoreChangedEvent(Long storeId, String name, Object data) {

    public record LayoutVersion(int version) {
    }

    public record StoreInfo(String name, boolean open) {
    }

    public static StoreChangedEvent status(Long storeId, StatusItem item) {
        return new StoreChangedEvent(storeId, "status", item);
    }

    public static StoreChangedEvent snapshot(Long storeId, StatusSnapshotResponse snapshot) {
        return new StoreChangedEvent(storeId, "snapshot", snapshot);
    }

    public static StoreChangedEvent layout(Long storeId, int version) {
        return new StoreChangedEvent(storeId, "layout", new LayoutVersion(version));
    }

    public static StoreChangedEvent store(Long storeId, String name, boolean open) {
        return new StoreChangedEvent(storeId, "store", new StoreInfo(name, open));
    }
}
