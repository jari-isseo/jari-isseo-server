package com.jariisseo.customer;

import com.jariisseo.store.StoreRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 손님 공개 스냅샷 API + /s/{publicId} 페이지 (API 정의서 5장·6장). 로컬 MySQL, rollback
@SpringBootTest
@Transactional
class CustomerApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;

    MockMvc mvc;
    String token;
    String publicId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        String loginId = "t" + System.nanoTime() % 1_000_000_000_000L;
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "password123", "storeName": "Cafe Moon"}
                                """.formatted(loginId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = JsonPath.read(body, "$.accessToken");
        publicId = JsonPath.read(body, "$.store.publicId");
    }

    @Test
    @DisplayName("스냅샷: 토큰 없이 { store, layout, statuses, lastUpdatedAt } 를 받는다")
    void snapshot() throws Exception {
        saveLayout("t-a", "t-b");
        mvc.perform(put("/api/owner/tables/t-a/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/public/stores/" + publicId))   // Authorization 헤더 없음
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.publicId").value(publicId))
                .andExpect(jsonPath("$.store.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.store.open").value(true))
                .andExpect(jsonPath("$.layout.version").value(1))
                .andExpect(jsonPath("$.layout.unit").value("m"))
                .andExpect(jsonPath("$.layout.floors[0].tables.length()").value(2))
                .andExpect(jsonPath("$.statuses.length()").value(2))
                .andExpect(jsonPath("$.statuses[0].tableId").value("t-a"))
                .andExpect(jsonPath("$.statuses[0].occupied").value(true))
                .andExpect(jsonPath("$.statuses[1].occupied").value(false))
                .andExpect(jsonPath("$.lastUpdatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("배치가 아직 없으면 layout: null, statuses: [], lastUpdatedAt: null")
    void snapshotWithoutLayout() throws Exception {
        mvc.perform(get("/api/public/stores/" + publicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.layout").value(nullValue()))
                .andExpect(jsonPath("$.statuses.length()").value(0))
                .andExpect(jsonPath("$.lastUpdatedAt").value(nullValue()));
    }

    @Test
    @DisplayName("없는 publicId 면 404 STORE_NOT_FOUND")
    void unknownStore() throws Exception {
        mvc.perform(get("/api/public/stores/nostore1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    @Test
    @DisplayName("만료·잘못된 토큰이 붙어 와도 공개 API 는 막히지 않는다")
    void staleTokenDoesNotBlockPublic() throws Exception {
        mvc.perform(get("/api/public/stores/" + publicId).header("Authorization", "Bearer broken-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("완료 조건: /s/{publicId} 에 접속하면 렌더러 페이지가 열린다 (css·js 기준 경로 /viewer/)")
    void viewerPage() throws Exception {
        mvc.perform(get("/s/" + publicId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<base href=\"/viewer/\">")))
                .andExpect(content().string(containsString("renderer.js")));
    }

    @Test
    @DisplayName("없는 매장의 /s/ 페이지는 404 (페이지는 내려주고 화면에서 안내)")
    void viewerPageUnknownStore() throws Exception {
        mvc.perform(get("/s/nostore1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("renderer.js")));
    }

    private void saveLayout(String... tableIds) throws Exception {
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
    }
}
