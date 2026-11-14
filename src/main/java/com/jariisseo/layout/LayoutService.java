package com.jariisseo.layout;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.dto.LayoutData;
import com.jariisseo.layout.dto.LayoutResponse;
import com.jariisseo.status.StatusSource;
import com.jariisseo.status.TableStatus;
import com.jariisseo.status.TableStatusRepository;
import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LayoutService {

    private final LayoutRepository layoutRepository;
    private final StoreRepository storeRepository;
    private final TableStatusRepository tableStatusRepository;
    private final ObjectMapper objectMapper;   // 스프링 부트가 만들어 둔 JSON 변환기 (Jackson 3)

    public LayoutService(LayoutRepository layoutRepository, StoreRepository storeRepository,
                         TableStatusRepository tableStatusRepository, ObjectMapper objectMapper) {
        this.layoutRepository = layoutRepository;
        this.storeRepository = storeRepository;
        this.tableStatusRepository = tableStatusRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public LayoutResponse getMyLayout(Long storeId) {
        Layout layout = layoutRepository.findByStoreId(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.LAYOUT_NOT_FOUND));
        LayoutData data = objectMapper.readValue(layout.getData(), LayoutData.class);
        return new LayoutResponse(layout.getVersion(), data.unit(), data.floors());
    }

    // 배치 저장 (항상 모든 층을 포함한 배치 전체). 처음이면 version 1, 다시 저장하면 +1
    @Transactional
    public LayoutResponse saveMyLayout(Long storeId, LayoutData request) {
        LayoutData data = LayoutValidator.normalize(request);   // 규칙 위반이면 400 INVALID_LAYOUT (잠그기 전에 먼저)

        // 매장 행을 잠가서 같은 매장의 저장을 한 번에 하나씩 처리
        //   잠그지 않으면 두 저장이 같은 옛 상태를 읽고 각자 써서, version 이 한 번만 오르거나
        //   테이블 상태가 실제 배치와 어긋날 수 있다. 조회(GET)는 잠그지 않는다
        Store store = storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        String json = objectMapper.writeValueAsString(data);

        Layout layout = layoutRepository.findByStoreId(storeId)
                .map(existing -> {
                    existing.replace(json);
                    return existing;
                })
                .orElseGet(() -> layoutRepository.save(new Layout(store, json)));

        syncTableStatuses(storeId, data);
        // 저장 후 SSE layout 이벤트는 SSE 이슈(4-2)에서 추가

        return new LayoutResponse(layout.getVersion(), data.unit(), data.floors());
    }

    // 테이블 상태 유지 규칙 (API 정의서 4장)
    //   같은 id → 상태 유지 / 새 id → 손님 없음(false)으로 시작 / 사라진 id → 상태 삭제
    //   층이 통째로 사라지면 그 층 테이블 id 도 모두 '사라진 id' 가 되어 함께 삭제된다
    private void syncTableStatuses(Long storeId, LayoutData data) {
        Set<String> tableIds = data.floors().stream()
                .flatMap(floor -> floor.tables().stream())
                .map(LayoutData.TableData::id)
                .collect(Collectors.toSet());

        List<TableStatus> existing = tableStatusRepository.findByIdStoreId(storeId);
        Set<String> existingIds = existing.stream()
                .map(TableStatus::getTableId)
                .collect(Collectors.toSet());

        List<TableStatus> removed = existing.stream()
                .filter(status -> !tableIds.contains(status.getTableId()))
                .toList();
        tableStatusRepository.deleteAll(removed);

        List<TableStatus> added = tableIds.stream()
                .filter(id -> !existingIds.contains(id))
                .map(id -> new TableStatus(storeId, id, false, StatusSource.MANUAL))
                .toList();
        tableStatusRepository.saveAll(added);
    }
}
