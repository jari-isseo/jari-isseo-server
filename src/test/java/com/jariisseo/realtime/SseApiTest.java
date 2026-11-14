package com.jariisseo.realtime;

import com.jariisseo.store.StoreRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 손님 SSE 구독 (API 정의서 5장, #18). 로컬 MySQL, rollback
//   MockMvc 는 연결이 열린 채로 결과를 돌려주므로, 지금까지 보낸 내용을 응답 본문에서 읽어 확인한다
@SpringBootTest
@Transactional
class SseApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    StoreEventHub hub;

    MockMvc mvc;
    String token;
    String publicId;
    Long storeId;

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
        storeId = storeRepository.findByPublicId(publicId).orElseThrow().getId();
    }

    @Test
    @DisplayName("완료 조건: 연결하면 text/event-stream 으로 snapshot 이벤트를 먼저 받는다")
    void snapshotOnConnect() throws Exception {
        saveLayout();
        mvc.perform(put("/api/owner/tables/t-a/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupied\": true}"))
                .andExpect(status().isOk());

        MvcResult result = connect();   // Authorization 헤더 없음

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        String stream = body(result);
        assertThat(stream).startsWith("event:snapshot\ndata:");
        String data = dataOf(stream, "snapshot");
        assertThat((Object) JsonPath.read(data, "$.statuses.length()")).isEqualTo(2);
        assertThat((Object) JsonPath.read(data, "$.statuses[0].tableId")).isEqualTo("t-a");
        assertThat((Object) JsonPath.read(data, "$.statuses[0].occupied")).isEqualTo(true);
        assertThat((String) JsonPath.read(data, "$.lastUpdatedAt")).isNotBlank();
        assertThat(hub.subscriberCount(storeId)).isEqualTo(1);
    }

    @Test
    @DisplayName("배치가 아직 없으면 snapshot 은 빈 statuses, lastUpdatedAt null")
    void snapshotWithoutLayout() throws Exception {
        String data = dataOf(body(connect()), "snapshot");
        assertThat((Object) JsonPath.read(data, "$.statuses.length()")).isEqualTo(0);
        assertThat((Object) JsonPath.read(data, "$.lastUpdatedAt")).isNull();
    }

    @Test
    @DisplayName("완료 조건: 하트비트로 :ping 주석이 간다")
    void heartbeat() throws Exception {
        MvcResult result = connect();
        hub.sendHeartbeat();   // 20초 기다리지 않고 직접 호출
        assertThat(body(result)).contains("\n:ping\n");
    }

    @Test
    @DisplayName("broadcast 한 이벤트가 같은 매장 구독자에게 간다 (#19 에서 상태 변경과 연결)")
    void broadcast() throws Exception {
        MvcResult result = connect();
        hub.broadcast(storeId, "status", Map.of("tableId", "t-a", "occupied", true));
        String data = dataOf(body(result), "status");
        assertThat((Object) JsonPath.read(data, "$.tableId")).isEqualTo("t-a");
    }

    @Test
    @DisplayName("완료 조건: 연결이 끝나면 구독자 목록에서 빠진다")
    void removedOnDisconnect() throws Exception {
        MvcResult result = connect();
        assertThat(hub.subscriberCount(storeId)).isEqualTo(1);

        ((MockAsyncContext) result.getRequest().getAsyncContext()).complete();   // 손님이 페이지를 닫음

        assertThat(hub.subscriberCount(storeId)).isZero();
    }

    @Test
    @DisplayName("타임아웃(30분)이 되면 구독자 목록에서 빠진다")
    void removedOnTimeout() throws Exception {
        MvcResult result = connect();
        MockAsyncContext async = (MockAsyncContext) result.getRequest().getAsyncContext();

        for (AsyncListener listener : async.getListeners()) {   // 30분 기다리지 않고 타임아웃 신호만 보냄
            listener.onTimeout(new AsyncEvent(async));
        }

        assertThat(hub.subscriberCount(storeId)).isZero();
    }

    @Test
    @DisplayName("없는 publicId 면 연결하지 않고 404 STORE_NOT_FOUND (JSON)")
    void unknownStore() throws Exception {
        mvc.perform(get("/api/public/stores/nostore1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("STORE_NOT_FOUND"));
    }

    private MvcResult connect() throws Exception {
        return mvc.perform(get("/api/public/stores/" + publicId + "/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // "event:{name}\ndata:{json}\n" 에서 json 부분
    private static String dataOf(String stream, String eventName) {
        String marker = "event:" + eventName + "\ndata:";
        int start = stream.indexOf(marker);
        assertThat(start).as("event:%s 없음 → %s", eventName, stream).isGreaterThanOrEqualTo(0);
        start += marker.length();
        return stream.substring(start, stream.indexOf('\n', start));
    }

    private void saveLayout() throws Exception {
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
}
