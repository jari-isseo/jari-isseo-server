package com.jariisseo.layout;

import com.jariisseo.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

// 매장 배치 (API 정의서 8장). 매장당 1개.
// 배치는 항상 통째로 읽고 쓰므로 2장 Layout JSON 전체를 MySQL JSON 컬럼 하나에 저장한다.
@Entity
@Table(name = "layout")
public class Layout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    // 서버가 저장할 때마다 +1 (2장). 처음 저장하면 1
    @Column(nullable = false)
    private int version;

    // 2장 Layout JSON 전체 (floors, unit 등).
    // columnDefinition 을 안 쓰면 varchar(255) 가 되어 배치가 잘림 → MySQL JSON 타입으로 지정
    // 자바에서는 문자열 그대로 다루고, JSON 해석·검증은 배치 저장 API 이슈에서 한다
    @Column(nullable = false, columnDefinition = "json")
    private String data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Layout() {
    }

    public Layout(Store store, String data) {
        this.store = store;
        this.data = data;
        this.version = 1;
    }

    // 배치 재저장: 통째로 바꾸고 version +1
    public void replace(String data) {
        this.data = data;
        this.version++;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Store getStore() {
        return store;
    }

    public int getVersion() {
        return version;
    }

    public String getData() {
        return data;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
