package com.jariisseo.layout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LayoutRepository extends JpaRepository<Layout, Long> {

    // store.id 로 찾기 (매장당 배치 1개)
    Optional<Layout> findByStoreId(Long storeId);
}
