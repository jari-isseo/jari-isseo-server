package com.jariisseo.customer;

import com.jariisseo.customer.dto.SnapshotResponse;
import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.LayoutService;
import com.jariisseo.status.TableStatusRepository;
import com.jariisseo.status.dto.StatusSnapshotResponse;
import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerSnapshotService {

    private final StoreRepository storeRepository;
    private final LayoutService layoutService;
    private final TableStatusRepository tableStatusRepository;

    public CustomerSnapshotService(StoreRepository storeRepository, LayoutService layoutService,
                                   TableStatusRepository tableStatusRepository) {
        this.storeRepository = storeRepository;
        this.layoutService = layoutService;
        this.tableStatusRepository = tableStatusRepository;
    }

    // 손님 웹 첫 화면용 스냅샷. 빈 테이블 수·전체 수는 웹이 statuses 로 계산한다 (FR-U05)
    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshot(String publicId) {
        Store store = storeRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));

        // 전체 비우기 응답(#15)과 같은 방식으로 statuses·lastUpdatedAt 을 만든다
        StatusSnapshotResponse statuses = StatusSnapshotResponse.of(
                tableStatusRepository.findByIdStoreId(store.getId()));

        return new SnapshotResponse(
                new SnapshotResponse.StoreSummary(store.getPublicId(), store.getName(), store.isOpen()),
                layoutService.findLayout(store.getId()).orElse(null),
                statuses.statuses(),
                statuses.lastUpdatedAt());
    }
}
