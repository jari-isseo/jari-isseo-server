package com.jariisseo.status;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.status.dto.StatusSnapshotResponse;
import com.jariisseo.status.dto.TableStatusResponse;
import com.jariisseo.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TableStatusService {

    private final TableStatusRepository tableStatusRepository;
    private final StoreRepository storeRepository;

    public TableStatusService(TableStatusRepository tableStatusRepository, StoreRepository storeRepository) {
        this.tableStatusRepository = tableStatusRepository;
        this.storeRepository = storeRepository;
    }

    // 테이블 1개 상태 바꾸기
    //   - (토큰의 매장, tableId) 로 찾으므로 다른 매장의 테이블은 찾을 수 없음 → 404 TABLE_NOT_FOUND
    //   - 이미 그 값이면 아무것도 바꾸지 않음 (updatedAt 도 그대로) → 같은 요청 두 번 = 같은 결과
    @Transactional
    public TableStatusResponse changeStatus(Long storeId, String tableId, boolean occupied) {
        lockStore(storeId);
        TableStatus status = tableStatusRepository.findById(new TableStatusId(storeId, tableId))
                .orElseThrow(() -> new ApiException(ErrorCode.TABLE_NOT_FOUND));

        if (status.isOccupied() != occupied) {
            status.change(occupied, StatusSource.MANUAL);
            // 성공 시 SSE status 이벤트는 SSE 이슈(4-2)에서 추가
        }
        return TableStatusResponse.from(status);
    }

    // 전체 비우기: 모든 테이블을 손님 없음으로
    //   사장님이 '전부 비었음'을 확인한 것이므로 이미 비어 있던 테이블도 updatedAt 을 지금으로 → 정보 오래됨 표시가 사라짐
    @Transactional
    public StatusSnapshotResponse resetAll(Long storeId) {
        lockStore(storeId);
        List<TableStatus> statuses = tableStatusRepository.findByIdStoreId(storeId);
        statuses.forEach(status -> status.change(false, StatusSource.MANUAL));
        // SSE snapshot 이벤트는 SSE 이슈(4-2)에서 추가
        return StatusSnapshotResponse.of(statuses);
    }

    // 배치 저장(#14)과 같은 매장 행 잠금 → 배치 저장이 테이블 상태를 지우는 도중에 상태를 바꾸는 일이 없도록
    private void lockStore(Long storeId) {
        storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
