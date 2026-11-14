package com.jariisseo.store;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 매장 정보 조회·수정 API (API 정의서 4장). 로컬 MySQL, 끝나면 rollback
@SpringBootTest
@Transactional
class OwnerStoreApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;

    MockMvc mvc;
    String token;
    String publicId;

    // 테스트마다 새 사장님으로 가입해서 토큰과 publicId 를 받아 둔다
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
    @DisplayName("GET: 정의서 4장 형식 { publicId, name, open, shareUrl, hasLayout }")
    void getMyStore() throws Exception {
        mvc.perform(get("/api/owner/store").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicId))
                .andExpect(jsonPath("$.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.shareUrl").value(endsWith("/s/" + publicId)))
                .andExpect(jsonPath("$.hasLayout").value(false));
    }

    @Test
    @DisplayName("PATCH: 이름만 보내면 이름만 바뀌고 영업 상태는 그대로 (한글 이름 저장)")
    void patchNameOnly() throws Exception {
        patchStore("""
                {"name": "카페 문 성수점"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));

        Store store = storeRepository.findByPublicId(publicId).orElseThrow();
        assertThat(store.getName()).isEqualTo("카페 문 성수점");
        assertThat(store.isOpen()).isTrue();
    }

    @Test
    @DisplayName("PATCH: 영업 상태만 보내면 영업 상태만 바뀌고 이름은 그대로")
    void patchOpenOnly() throws Exception {
        patchStore("""
                {"open": false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    @DisplayName("PATCH: 빈 요청 {} 이면 아무것도 바뀌지 않는다")
    void patchNothing() throws Exception {
        patchStore("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.open").value(true));
    }

    @Test
    @DisplayName("PATCH: 공백 이름·50자 초과 이름은 400 INVALID_REQUEST")
    void patchInvalidName() throws Exception {
        patchStore("""
                {"name": "   "}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        patchStore("""
                {"name": "%s"}
                """.formatted("a".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("토큰 없이 PATCH → 401 UNAUTHORIZED")
    void patchWithoutToken() throws Exception {
        mvc.perform(patch("/api/owner/store").contentType(MediaType.APPLICATION_JSON).content("{\"open\": false}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private org.springframework.test.web.servlet.ResultActions patchStore(String json) throws Exception {
        return mvc.perform(patch("/api/owner/store")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
