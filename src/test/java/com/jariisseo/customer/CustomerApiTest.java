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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 손님 공개 스냅샷 API + /s/{publicId} 페이지 (API 정의서 5장·6장). 로컬 MySQL, rollback
//   PUBLIC_BASE_URL 은 끝에 / 를 붙여 둔다 → og 주소에서 // 가 생기지 않는지도 함께 확인 (#28)
@SpringBootTest(properties = "app.public-base-url=https://jari.example/")
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

    @Test
    @DisplayName("#28 완료 조건: /s/{publicId} 에 매장 이름 og 태그가 들어간다 (카톡 링크 미리보기)")
    void viewerPageOpenGraph() throws Exception {
        mvc.perform(get("/s/" + publicId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Cafe Moon 빈자리</title>")))
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"Cafe Moon 빈자리\">")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"https://jari.example/s/" + publicId + "\">")))
                .andExpect(content().string(containsString("<meta property=\"og:image\" content=\"https://jari.example/viewer/og.png\">")))
                .andExpect(content().string(containsString("og:description")))
                // og 태그도 <base> 와 같이 <head> 안에 있어야 미리보기가 읽는다
                .andExpect(content().string(containsString("<head>\n  <base href=\"/viewer/\">\n  <meta name=\"description\"")));
    }

    @Test
    @DisplayName("#28 매장 이름의 따옴표·꺾쇠·$ 는 이스케이프되어 태그를 깨지 못한다")
    void viewerPageOpenGraphEscapesStoreName() throws Exception {
        String evil = "A\"><script>alert(1)</script>&$1";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "password123", "storeName": "%s"}
                                """.formatted("e" + System.nanoTime() % 1_000_000_000_000L, evil.replace("\"", "\\\""))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String evilId = JsonPath.read(body, "$.store.publicId");

        mvc.perform(get("/s/" + evilId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("content=\"A&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;&amp;$1 빈자리\"")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    @DisplayName("#28 없는 매장은 서비스 이름으로 미리보기")
    void viewerPageOpenGraphUnknownStore() throws Exception {
        mvc.perform(get("/s/nostore1"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("<meta property=\"og:title\" content=\"자리있어?!\">")))
                .andExpect(content().string(containsString("매장을 찾을 수 없어요")));
    }

    @Test
    @DisplayName("#28 미리보기 이미지는 로그인 없이 받을 수 있다")
    void openGraphImage() throws Exception {
        mvc.perform(get("/viewer/og.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
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
