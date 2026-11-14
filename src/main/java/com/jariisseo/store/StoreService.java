package com.jariisseo.store;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.LayoutRepository;
import com.jariisseo.realtime.StoreChangedEvent;
import com.jariisseo.store.dto.MyStoreResponse;
import com.jariisseo.store.dto.UpdateStoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final LayoutRepository layoutRepository;
    private final String publicBaseUrl;
    private final ApplicationEventPublisher events;

    public StoreService(StoreRepository storeRepository, LayoutRepository layoutRepository,
                        @Value("${app.public-base-url}") String publicBaseUrl,
                        ApplicationEventPublisher events) {
        this.storeRepository = storeRepository;
        this.layoutRepository = layoutRepository;
        this.events = events;
        // 끝에 / 가 붙어 있어도 //s/ 가 되지 않게
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public MyStoreResponse getMyStore(Long storeId) {
        return toResponse(findStore(storeId));
    }

    // 보낸 필드만 수정. 트랜잭션이 끝날 때 바뀐 값이 DB 에 자동 반영된다 (JPA 변경 감지)
    @Transactional
    public MyStoreResponse updateMyStore(Long storeId, UpdateStoreRequest request) {
        Store store = findStore(storeId);
        boolean changed = false;
        if (request.name() != null && !request.name().strip().equals(store.getName())) {
            store.rename(request.name().strip());
            changed = true;
        }
        if (request.open() != null && request.open() != store.isOpen()) {
            store.changeOpen(request.open());
            changed = true;
        }
        // 실제로 바뀐 경우에만 손님 화면에 store 이벤트 (이름·영업 상태 갱신)
        if (changed) {
            events.publishEvent(StoreChangedEvent.store(storeId, store.getName(), store.isOpen()));
        }
        return toResponse(store);
    }

    // 토큰은 유효한데 매장이 지워진 경우 → 다시 로그인하도록 401
    private Store findStore(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private MyStoreResponse toResponse(Store store) {
        return new MyStoreResponse(
                store.getPublicId(),
                store.getName(),
                store.isOpen(),
                publicBaseUrl + "/s/" + store.getPublicId(),
                layoutRepository.existsByStoreId(store.getId()));
    }
}
