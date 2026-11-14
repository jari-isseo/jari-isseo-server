package com.jariisseo.store;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.LayoutRepository;
import com.jariisseo.store.dto.MyStoreResponse;
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
        // 토큰은 유효한데 매장이 지워진 경우 → 다시 로그인하도록 401
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        return new MyStoreResponse(
                store.getPublicId(),
                store.getName(),
                store.isOpen(),
                publicBaseUrl + "/s/" + store.getPublicId(),
                layoutRepository.existsByStoreId(storeId));
    }
}
