package com.jariisseo.status;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

// 테이블 손님 있음/없음 (API 정의서 8장). 자주 바뀌므로 배치(layout)와 분리
@Entity
@Table(name = "table_status")
public class TableStatus {

    @EmbeddedId
    private TableStatusId id;

    @Column(nullable = false)
    private boolean occupied;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusSource source;

    // 응답·SSE 의 updatedAt 으로 그대로 나가는 값 (UTC)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TableStatus() {
    }

    public TableStatus(Long storeId, String tableId, boolean occupied, StatusSource source) {
        this.id = new TableStatusId(storeId, tableId);
        this.occupied = occupied;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    public void change(boolean occupied, StatusSource source) {
        this.occupied = occupied;
        this.source = source;
        this.updatedAt = Instant.now();
    }

    public TableStatusId getId() {
        return id;
    }

    public String getTableId() {
        return id.getTableId();
    }

    public boolean isOccupied() {
        return occupied;
    }

    public StatusSource getSource() {
        return source;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
