package com.jariisseo.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

// 매장 (API 정의서 8장). 매장당 계정이 1개라 로그인 정보도 여기에 함께 둔다.
@Entity
@Table(name = "store")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 손님 링크용 ID (/s/{publicId}). 추측 어려운 8자리 → PublicIdGenerator
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(nullable = false, length = 50)
    private String name;

    // 영업 중 여부
    @Column(name = "open", nullable = false)
    private boolean open;

    @Column(name = "login_id", nullable = false, unique = true, length = 30)
    private String loginId;

    // 비밀번호 원문이 아닌 해시(BCrypt)만 저장 (#12)
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // JPA 가 DB 에서 읽어올 때 쓰는 기본 생성자 (직접 쓰지 않음)
    protected Store() {
    }

    public Store(String publicId, String name, String loginId, String passwordHash) {
        this.publicId = publicId;
        this.name = name;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.open = true;   // 가입하면 영업 중으로 시작
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeOpen(boolean open) {
        this.open = open;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getName() {
        return name;
    }

    public boolean isOpen() {
        return open;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
