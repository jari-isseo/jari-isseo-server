package com.jariisseo.status.dto;

import com.jariisseo.status.StatusSource;
import com.jariisseo.status.TableStatus;

import java.time.Instant;

// 200 OK: { "tableId": "t-3f9a", "occupied": true, "source": "MANUAL", "updatedAt": "2026-11-12T11:30:00Z" }
public record TableStatusResponse(String tableId, boolean occupied, StatusSource source, Instant updatedAt) {

    public static TableStatusResponse from(TableStatus status) {
        return new TableStatusResponse(status.getTableId(), status.isOccupied(), status.getSource(), status.getUpdatedAt());
    }
}
