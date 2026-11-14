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
        // 매장 행을 잠가서 같은 매장의 수정을 한 번에 하나씩 처리
        //   잠그지 않으면 이름 수정과 영업 상태 수정이 동시에 오면, 각자 상대 필드의 옛 값을 store 이벤트로 보낼 수 있다
        //   (DB 에는 둘 다 반영되지만 손님 화면에는 옛 값이 잠깐 보임). 조회(GET)는 잠그지 않는다
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
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
