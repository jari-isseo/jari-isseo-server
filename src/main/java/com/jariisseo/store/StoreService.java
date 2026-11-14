package com.jariisseo.store;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.LayoutRepository;
import com.jariisseo.store.dto.MyStoreResponse;
import com.jariisseo.store.dto.UpdateStoreRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final LayoutRepository layoutRepository;
    private final String publicBaseUrl;

    public StoreService(StoreRepository storeRepository, LayoutRepository layoutRepository,
                        @Value("${app.public-base-url}") String publicBaseUrl) {
        this.storeRepository = storeRepository;
        this.layoutRepository = layoutRepository;
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
        if (request.name() != null) {
            store.rename(request.name().strip());
        }
        if (request.open() != null) {
            store.changeOpen(request.open());
            // open 변경 시 SSE store 이벤트는 SSE 이슈(4-2)에서 추가
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
