package com.jariisseo.realtime;

import com.jariisseo.layout.LayoutRepository;
import com.jariisseo.status.TableStatusRepository;
import com.jariisseo.store.StoreRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 사장님 변경 → 손님 SSE 이벤트 (API 정의서 5장, #19)
//   이벤트는 '커밋된 뒤'에 나가므로, 다른 테스트처럼 @Transactional 롤백을 쓰면 이벤트가 영영 나가지 않는다.
//   → 이 테스트만 실제로 커밋하고, 끝나면 만든 매장 데이터를 직접 지운다 (@AfterEach)
@SpringBootTest
class SseEventPublishTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    LayoutRepository layoutRepository;
    @Autowired
    TableStatusRepository tableStatusRepository;

    MockMvc mvc;
    final List<Long> createdStoreIds = new ArrayList<>();

    String token;
    String publicId;
    MvcResult sse;   // 손님 화면 하나가 구독 중인 상태

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String[] store = signup();
        token = store[0];
        publicId = store[1];
        saveLayout(token);
        sse = connect(publicId);
    }

    @AfterEach
    void cleanUp() {
        for (Long storeId : createdStoreIds) {
            tableStatusRepository.deleteAll(tableStatusRepository.findByIdStoreId(storeId));
            layoutRepository.findByStoreId(storeId).ifPresent(layoutRepository::delete);
            storeRepository.deleteById(storeId);
        }
    }

    @Test
    @DisplayName("완료 조건: 상태 전환 → status 이벤트 { tableId, occupied, updatedAt }")
    void statusEvent() throws Exception {
        changeStatus("t-a", true);

        String data = lastData("status");
        assertThat((String) JsonPath.read(data, "$.tableId")).isEqualTo("t-a");
        assertThat((Boolean) JsonPath.read(data, "$.occupied")).isTrue();
        assertThat((String) JsonPath.read(data, "$.updatedAt")).isNotBlank();
        assertThat(data).doesNotContain("source");   // 정의서 5장 모양 그대로 (source 는 사장님 응답에만)
    }

    @Test
    @DisplayName("같은 상태로 다시 보내면 바뀐 게 없으므로 이벤트도 없다")
    void noEventWhenUnchanged() throws Exception {
        changeStatus("t-a", true);
        changeStatus("t-a", true);
        changeStatus("t-b", false);   // 처음부터 false

        assertThat(count("status")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")   // JsonPath 결과를 List<Boolean> 으로 꺼냄
    @DisplayName("완료 조건: 전체 비우기 → snapshot 이벤트로 상태 전체 교체")
    void snapshotEventOnReset() throws Exception {
        changeStatus("t-a", true);
        mvc.perform(post("/api/owner/tables/status/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(count("snapshot")).isEqualTo(2);   // 연결 직후 1번 + 비우기 1번
        String data = lastData("snapshot");
        assertThat((Integer) JsonPath.read(data, "$.statuses.length()")).isEqualTo(2);
        assertThat((List<Boolean>) JsonPath.read(data, "$.statuses[*].occupied")).containsOnly(false);
        assertThat((String) JsonPath.read(data, "$.lastUpdatedAt")).isNotBlank();
    }

    @Test
    @DisplayName("완료 조건: 배치 저장 → layout 이벤트 { version }")
    void layoutEvent() throws Exception {
        saveLayout(token);   // 두 번째 저장 → version 2

        assertThat(lastData("layout")).isEqualTo("{\"version\":2}");
    }

    @Test
    @DisplayName("완료 조건: 매장 수정 → store 이벤트 { name, open }, 바뀐 게 없으면 이벤트 없음")
    void storeEvent() throws Exception {
        patchStore("{\"open\": false}");
        String data = lastData("store");
        assertThat((String) JsonPath.read(data, "$.name")).isEqualTo("Cafe Moon");
        assertThat((Boolean) JsonPath.read(data, "$.open")).isFalse();

        patchStore("{\"name\": \"Cafe Moon\", \"open\": false}");   // 같은 값
        assertThat(count("store")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패한 요청(롤백)은 이벤트를 보내지 않는다")
    void noEventOnFailure() throws Exception {
        mvc.perform(put("/api/owner/tables/t-none/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": true}"))
                .andExpect(status().isNotFound());

        assertThat(count("status")).isZero();
    }

    @Test
    @DisplayName("다른 매장의 변경은 이 매장 손님에게 가지 않는다")
    void otherStoreIsolated() throws Exception {
        String otherToken = signup()[0];
        saveLayout(otherToken);
        mvc.perform(put("/api/owner/tables/t-a/status")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": true}"))
                .andExpect(status().isOk());

        assertThat(count("status")).isZero();
        assertThat(count("layout")).isZero();
    }

    // ── 도우미 ──

    private String[] signup() throws Exception {
        String loginId = "t" + System.nanoTime() % 1_000_000_000_000L;
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId": "%s", "password": "password123", "storeName": "Cafe Moon"}
                                """.formatted(loginId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String publicId = JsonPath.read(body, "$.store.publicId");
        createdStoreIds.add(storeRepository.findByPublicId(publicId).orElseThrow().getId());
        return new String[]{JsonPath.read(body, "$.accessToken"), publicId};
    }

    private void saveLayout(String token) throws Exception {
        mvc.perform(put("/api/owner/store/layout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unit": "m", "floors": [{"id": "f-1", "name": "1층", "order": 0,
                                  "floor": {"polygon": [[0,0],[20,0],[20,10],[0,10]]},
                                  "tables": [{"id": "t-a", "name": "1번", "x": 1, "z": 1},
                                             {"id": "t-b", "name": "2번", "x": 3, "z": 1}]}]}
                                """))
                .andExpect(status().isOk());
    }

    private MvcResult connect(String publicId) throws Exception {
        return mvc.perform(get("/api/public/stores/" + publicId + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void changeStatus(String tableId, boolean occupied) throws Exception {
        mvc.perform(put("/api/owner/tables/" + tableId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": " + occupied + "}"))
                .andExpect(status().isOk());
    }

    private void patchStore(String json) throws Exception {
        mvc.perform(patch("/api/owner/store")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private String stream() throws Exception {
        return sse.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int count(String eventName) throws Exception {
        String marker = "event:" + eventName + "\n";
        String s = stream();
        int n = 0;
        for (int i = s.indexOf(marker); i >= 0; i = s.indexOf(marker, i + 1)) {
            n++;
        }
        return n;
    }

    // 마지막으로 받은 해당 이벤트의 data JSON
    private String lastData(String eventName) throws Exception {
        String s = stream();
        String marker = "event:" + eventName + "\ndata:";
        int start = s.lastIndexOf(marker);
        assertThat(start).as("event:%s 없음 → %s", eventName, s).isGreaterThanOrEqualTo(0);
        start += marker.length();
        return s.substring(start, s.indexOf('\n', start));
    }
}
