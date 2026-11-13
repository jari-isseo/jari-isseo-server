package com.jariisseo.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    // 손님 웹: /s/{publicId}
    Optional<Store> findByPublicId(String publicId);

    // 로그인 (#12)
    Optional<Store> findByLoginId(String loginId);

    // 가입 시 아이디 중복 확인 → LOGIN_ID_TAKEN
    boolean existsByLoginId(String loginId);

    // publicId 충돌 확인 → PublicIdGenerator
    boolean existsByPublicId(String publicId);
}
