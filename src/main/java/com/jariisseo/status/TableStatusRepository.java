package com.jariisseo.status;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TableStatusRepository extends JpaRepository<TableStatus, TableStatusId> {

    // 매장의 모든 테이블 상태 (스냅샷, 전체 비우기)
    List<TableStatus> findByIdStoreId(Long storeId);
}
