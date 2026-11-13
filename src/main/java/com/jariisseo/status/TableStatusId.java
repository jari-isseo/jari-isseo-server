package com.jariisseo.status;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

// table_status 의 PK = store_id + table_id (두 칸을 합친 키)
//   tableId 는 배치 JSON 안의 테이블 id (예: t-3f9a). 매장이 다르면 같은 tableId 가 있어도 됨
@Embeddable
public class TableStatusId implements Serializable {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "table_id", nullable = false, length = 50)
    private String tableId;

    protected TableStatusId() {
    }

    public TableStatusId(Long storeId, String tableId) {
        this.storeId = storeId;
        this.tableId = tableId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public String getTableId() {
        return tableId;
    }

    // 키로 쓰는 클래스는 값이 같으면 같은 키로 판단해야 함
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableStatusId other)) return false;
        return Objects.equals(storeId, other.storeId) && Objects.equals(tableId, other.tableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, tableId);
    }
}
