package com.jariisseo.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 서버처럼 요청을 보내 보는 테스트 (서버를 켜지 않고 MockMvc 로 흉내).
// 로컬 MySQL(.env) 사용, 끝나면 rollback.
@SpringBootTest
@Transactional
class AuthApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        // springSecurity(): SecurityConfig 규칙(토큰 검사)까지 똑같이 적용
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("가입하면 매장이 함께 만들어지고 토큰이 나온다 (201)")
    void signupCreatesStore() throws Exception {
        String loginId = newLoginId();

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId, "password123", "Cafe Moon")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.store.publicId").value(org.hamcrest.Matchers.matchesPattern("[a-kmnp-z2-9]{8}")))
                .andExpect(jsonPath("$.store.name").value("Cafe Moon"));

        var store = storeRepository.findByLoginId(loginId).orElseThrow();
        assertThat(store.getPasswordHash()).isNotEqualTo("password123").startsWith("$2");   // BCrypt 해시로 저장
    }

    @Test
    @DisplayName("같은 아이디로 다시 가입하면 409 LOGIN_ID_TAKEN")
    void duplicateLoginId() throws Exception {
        String loginId = newLoginId();
        signup(loginId, "password123");

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId, "password456", "Other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));
    }

    @Test
    @DisplayName("아이디·비밀번호 형식이 틀리면 400 INVALID_REQUEST")
    void invalidSignup() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("AB", "short", "Cafe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("전각 공백(U+2003)만 있는 매장 이름은 400 INVALID_REQUEST")
    void storeNameOnlyWhitespace() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(newLoginId(), "password123", "\\u2003")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("한글 25자 비밀번호(75바이트)는 500 이 아니라 400 INVALID_REQUEST")
    void passwordOverBcryptByteLimit() throws Exception {
        String koreanPassword = "가".repeat(25);   // 25자지만 UTF-8 로 75바이트 > 72

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(newLoginId(), koreanPassword, "Cafe")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("만료·잘못된 토큰을 붙여도 로그인·가입은 막히지 않는다 (토큰은 /api/owner/** 에서만 검사)")
    void staleTokenDoesNotBlockAuth() throws Exception {
        String loginId = newLoginId();

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer expired-or-broken-token")
                        .content(signupJson(loginId, "password123", "Cafe Moon")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer expired-or-broken-token")
                        .content(loginJson(loginId, "password123")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 성공 → 토큰 + expiresIn 30일, 실패 → 401 INVALID_CREDENTIALS")
    void login() throws Exception {
        String loginId = newLoginId();
        signup(loginId, "password123");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(loginId, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(2592000));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(loginId, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody_here", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("토큰 없이 /api/owner/store → 401, 잘못된 토큰 → 401, 토큰 있으면 200")
    void ownerStoreNeedsToken() throws Exception {
        mvc.perform(get("/api/owner/store"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(get("/api/owner/store").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String token = signup(newLoginId(), "password123");

        mvc.perform(get("/api/owner/store").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cafe Moon"))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.shareUrl").value(org.hamcrest.Matchers.containsString("/s/")))
                .andExpect(jsonPath("$.hasLayout").value(false));
    }

    // 가입하고 받은 accessToken 을 돌려준다
    private String signup(String loginId, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(loginId, password, "Cafe Moon")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.accessToken");
    }

    // 테스트마다 겹치지 않는 아이디 (영문 소문자·숫자 4~20자)
    private static String newLoginId() {
        return "t" + System.nanoTime() % 1_000_000_000_000L;
    }

    private static String signupJson(String loginId, String password, String storeName) {
        return """
                {"loginId": "%s", "password": "%s", "storeName": "%s"}
                """.formatted(loginId, password, storeName);
    }

    private static String loginJson(String loginId, String password) {
        return """
                {"loginId": "%s", "password": "%s"}
                """.formatted(loginId, password);
    }
}
