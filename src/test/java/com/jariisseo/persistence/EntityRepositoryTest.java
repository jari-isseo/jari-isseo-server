package com.jariisseo.persistence;

import com.jariisseo.layout.Layout;
import com.jariisseo.layout.LayoutRepository;
import com.jariisseo.status.StatusSource;
import com.jariisseo.status.TableStatus;
import com.jariisseo.status.TableStatusId;
import com.jariisseo.status.TableStatusRepository;
import com.jariisseo.store.PublicIdGenerator;
import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 로컬 MySQL(.env)에 실제로 저장·조회해 보는 테스트.
// @Transactional: 테스트가 끝나면 저장한 데이터를 되돌려(rollback) DB 를 깨끗하게 유지한다.
// flushAndClear(): 메모리(캐시)가 아니라 DB 에서 다시 읽어오게 해서 '진짜 저장됐는지' 확인한다.
@SpringBootTest
@Transactional
class EntityRepositoryTest {

    @Autowired
    StoreRepository storeRepository;
    @Autowired
    LayoutRepository layoutRepository;
    @Autowired
    TableStatusRepository tableStatusRepository;
    @Autowired
    PublicIdGenerator publicIdGenerator;

    @PersistenceContext
    EntityManager em;

    @Test
    @DisplayName("매장을 저장하고 publicId·loginId 로 찾는다")
    void storeSaveAndFind() {
        Store saved = saveStore("카페 문");
        flushAndClear();

        Store found = storeRepository.findByPublicId(saved.getPublicId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("카페 문");
        assertThat(found.isOpen()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(storeRepository.existsByLoginId(saved.getLoginId())).isTrue();
        assertThat(storeRepository.findByLoginId(saved.getLoginId())).isPresent();
    }

    @Test
    @DisplayName("배치 JSON 을 저장하고, 다시 저장하면 version 이 1 → 2")
    void layoutJsonAndVersion() {
        Store store = saveStore("카페 문");
        layoutRepository.save(new Layout(store, """
                {"unit": "m", "floors": []}
                """));
        flushAndClear();

        Layout first = layoutRepository.findByStoreId(store.getId()).orElseThrow();
        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(first.getData()).contains("\"floors\"");
        assertThat(first.getUpdatedAt()).isNotNull();

        first.replace("""
                {"unit": "m", "floors": [{"id": "f-1a2b", "name": "1층"}]}
                """);
        flushAndClear();

        Layout second = layoutRepository.findByStoreId(store.getId()).orElseThrow();
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(second.getData()).contains("f-1a2b").contains("1층");   // 한글도 깨지지 않음
    }

    @Test
    @DisplayName("테이블 상태를 (매장, 테이블) 복합 키로 저장·조회·변경한다")
    void tableStatusCompositeKey() {
        Store store = saveStore("카페 문");
        tableStatusRepository.save(new TableStatus(store.getId(), "t-3f9a", false, StatusSource.MANUAL));
        tableStatusRepository.save(new TableStatus(store.getId(), "t-8c21", true, StatusSource.MANUAL));
        flushAndClear();

        List<TableStatus> all = tableStatusRepository.findByIdStoreId(store.getId());
        assertThat(all).extracting(TableStatus::getTableId).containsExactlyInAnyOrder("t-3f9a", "t-8c21");

        TableStatus status = tableStatusRepository.findById(new TableStatusId(store.getId(), "t-3f9a")).orElseThrow();
        assertThat(status.isOccupied()).isFalse();
        status.change(true, StatusSource.MANUAL);
        flushAndClear();

        assertThat(tableStatusRepository.findById(new TableStatusId(store.getId(), "t-3f9a")).orElseThrow().isOccupied())
                .isTrue();
    }

    @Test
    @DisplayName("다른 매장이면 같은 tableId 를 써도 된다")
    void sameTableIdInDifferentStores() {
        Store a = saveStore("카페 A");
        Store b = saveStore("카페 B");
        tableStatusRepository.save(new TableStatus(a.getId(), "t-1", false, StatusSource.MANUAL));
        tableStatusRepository.save(new TableStatus(b.getId(), "t-1", true, StatusSource.MANUAL));
        flushAndClear();

        assertThat(tableStatusRepository.findByIdStoreId(a.getId())).hasSize(1);
        assertThat(tableStatusRepository.findByIdStoreId(b.getId())).hasSize(1);
    }

    private Store saveStore(String name) {
        String loginId = "test-" + System.nanoTime();   // 테스트마다 겹치지 않는 아이디
        return storeRepository.save(new Store(publicIdGenerator.generate(), name, loginId, "{noop}test"));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
