package com.jariisseo.realtime;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.status.TableStatusRepository;
import com.jariisseo.status.dto.StatusSnapshotResponse;
import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StoreEventService {

    private final StoreRepository storeRepository;
    private final TableStatusRepository tableStatusRepository;
    private final StoreEventHub hub;

    public StoreEventService(StoreRepository storeRepository, TableStatusRepository tableStatusRepository,
                             StoreEventHub hub) {
        this.storeRepository = storeRepository;
        this.tableStatusRepository = tableStatusRepository;
        this.hub = hub;
    }

    // 손님 화면 구독. 없는 publicId 면 연결을 열기 전에 404 STORE_NOT_FOUND
    //   snapshot 이벤트 data = { statuses, lastUpdatedAt } (전체 비우기 응답·스냅샷 API 의 statuses 와 같은 모양)
    public SseEmitter subscribe(String publicId) {
        Store store = storeRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        Long storeId = store.getId();
        return hub.subscribe(storeId,
                () -> StatusSnapshotResponse.of(tableStatusRepository.findByIdStoreId(storeId)));
    }
}
