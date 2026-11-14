package com.jariisseo.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    // 매장 행을 잠그고 읽기 (SELECT ... FOR UPDATE)
    //   같은 매장의 배치 저장이 동시에 두 번 와도, 앞의 저장이 끝날 때까지 뒤의 저장은 기다린다 (#14)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Store s where s.id = :id")
    Optional<Store> findByIdForUpdate(@Param("id") Long id);

    // 손님 웹: /s/{publicId}
    Optional<Store> findByPublicId(String publicId);

    // 로그인 (#12)
    Optional<Store> findByLoginId(String loginId);

    // 가입 시 아이디 중복 확인 → LOGIN_ID_TAKEN
    boolean existsByLoginId(String loginId);

    // publicId 충돌 확인 → PublicIdGenerator
    boolean existsByPublicId(String publicId);
}
