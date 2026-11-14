package com.jariisseo.status;

import com.jariisseo.store.StoreRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 테이블 상태 전환·전체 비우기 API (API 정의서 4장). 로컬 MySQL, rollback
@SpringBootTest
@Transactional
class TableStatusApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    TableStatusRepository tableStatusRepository;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("상태 전환: 바꿀 값을 보내면 그 값으로 바뀌고 { tableId, occupied, source, updatedAt } 응답")
    void changeStatus() throws Exception {
        Owner owner = newOwnerWithTables("t-a", "t-b");

        putStatus(owner, "t-a", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value("t-a"))
                .andExpect(jsonPath("$.occupied").value(true))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        assertThat(occupied(owner, "t-a")).isTrue();
        assertThat(occupied(owner, "t-b")).isFalse();   // 다른 테이블은 그대로

        putStatus(owner, "t-a", false).andExpect(jsonPath("$.occupied").value(false));
        assertThat(occupied(owner, "t-a")).isFalse();
    }

    @Test
    @DisplayName("완료 조건: 같은 요청을 두 번 보내도 결과가 같다 (멱등)")
    void sameRequestTwiceGivesSameResult() throws Exception {
        Owner owner = newOwnerWithTables("t-a");

        String first = body(putStatus(owner, "t-a", true));
        String second = body(putStatus(owner, "t-a", true));

        assertThat(second).isEqualTo(first);   // updatedAt 까지 같음 (이미 그 값이면 바꾸지 않음)
        assertThat(occupied(owner, "t-a")).isTrue();
    }

    @Test
    @DisplayName("없는 테이블이면 404 TABLE_NOT_FOUND")
    void unknownTable() throws Exception {
        Owner owner = newOwnerWithTables("t-a");

        putStatus(owner, "t-zzzz", true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));
    }

    @Test
    @DisplayName("완료 조건: 다른 매장 토큰으로는 바뀌지 않는다")
    void otherStoreCannotChange() throws Exception {
        Owner a = newOwnerWithTables("t-a", "t-only-a");
        Owner b = newOwnerWithTables("t-a");   // 다른 매장이면 같은 tableId 를 써도 됨

        // B 의 토큰으로 A 에만 있는 테이블 → 404, A 의 상태는 그대로
        putStatus(b, "t-only-a", true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_NOT_FOUND"));
        assertThat(occupied(a, "t-only-a")).isFalse();

        // 같은 이름 t-a: B 가 바꾸면 B 의 t-a 만 바뀐다
        putStatus(b, "t-a", true).andExpect(status().isOk());
        assertThat(occupied(b, "t-a")).isTrue();
        assertThat(occupied(a, "t-a")).isFalse();
    }

    @Test
    @DisplayName("occupied 를 안 보내면 400 INVALID_REQUEST, 토큰 없으면 401")
    void badRequestAndUnauthorized() throws Exception {
        Owner owner = newOwnerWithTables("t-a");

        mvc.perform(put("/api/owner/tables/t-a/status")
                        .header("Authorization", "Bearer " + owner.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/owner/tables/t-a/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/owner/tables/status/reset"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("전체 비우기: 모든 테이블이 손님 없음, 스냅샷과 같은 statuses + lastUpdatedAt 응답")
    void resetAll() throws Exception {
        Owner owner = newOwnerWithTables("t-a", "t-b", "t-c");
        putStatus(owner, "t-a", true);
        putStatus(owner, "t-c", true);

        mvc.perform(post("/api/owner/tables/status/reset").header("Authorization", "Bearer " + owner.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses.length()").value(3))
                .andExpect(jsonPath("$.statuses[0].tableId").value("t-a"))
                .andExpect(jsonPath("$.statuses[*].occupied").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.lastUpdatedAt").isNotEmpty());

        assertThat(tableStatusRepository.findByIdStoreId(owner.storeId)).allMatch(s -> !s.isOccupied());
    }

    @Test
    @DisplayName("전체 비우기는 다른 매장에 영향을 주지 않는다")
    void resetOnlyMyStore() throws Exception {
        Owner a = newOwnerWithTables("t-a");
        Owner b = newOwnerWithTables("t-a");
        putStatus(a, "t-a", true);

        mvc.perform(post("/api/owner/tables/status/reset").header("Authorization", "Bearer " + b.token))
                .andExpect(status().isOk());

        assertThat(occupied(a, "t-a")).isTrue();
    }

    // ---------- 도우미 ----------

    record Owner(String token, Long storeId) {
    }

    // 가입 → 테이블이 있는 배치 저장 (배치 저장 시 테이블 상태가 손님 없음으로 만들어짐, #14)
    private Owner newOwnerWithTables(String... tableIds) throws Exception {
        String loginId = "t" + System.nanoTime() % 1_000_000_000_000L;
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "password123", "storeName": "Cafe"}
                                """.formatted(loginId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = JsonPath.read(body, "$.accessToken");

        StringBuilder tables = new StringBuilder();
        for (int i = 0; i < tableIds.length; i++) {
            if (i > 0) tables.append(',');
            tables.append("""
                    {"id": "%s", "name": "%d번", "x": %d, "z": 1}""".formatted(tableIds[i], i + 1, i + 1));
        }
        mvc.perform(put("/api/owner/store/layout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unit": "m", "floors": [{"id": "f-1", "name": "1층", "order": 0,
                                  "floor": {"polygon": [[0,0],[20,0],[20,10],[0,10]]}, "tables": [%s]}]}
                                """.formatted(tables)))
                .andExpect(status().isOk());

        return new Owner(token, storeRepository.findByLoginId(loginId).orElseThrow().getId());
    }

    private ResultActions putStatus(Owner owner, String tableId, boolean occupied) throws Exception {
        return mvc.perform(put("/api/owner/tables/" + tableId + "/status")
                .header("Authorization", "Bearer " + owner.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"occupied\": " + occupied + "}"));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private boolean occupied(Owner owner, String tableId) {
        return tableStatusRepository.findById(new TableStatusId(owner.storeId, tableId)).orElseThrow().isOccupied();
    }
}
