package com.jariisseo.layout;

import com.jariisseo.status.StatusSource;
import com.jariisseo.status.TableStatus;
import com.jariisseo.status.TableStatusId;
import com.jariisseo.status.TableStatusRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 배치 조회·저장 API + 검증 + 테이블 상태 유지 규칙 (API 정의서 2장·4장). 로컬 MySQL, rollback
@SpringBootTest
@Transactional
class LayoutApiTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    StoreRepository storeRepository;
    @Autowired
    TableStatusRepository tableStatusRepository;

    MockMvc mvc;
    String token;
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
        storeId = storeRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    // ---------- 완료 조건 1: 샘플 JSON 저장·조회 ----------

    @Test
    @DisplayName("샘플 JSON(docs/samples/sample-layout.json)을 저장하고 그대로 조회한다")
    void saveAndGetSample() throws Exception {
        String sample = Files.readString(Path.of("docs/samples/sample-layout.json"), StandardCharsets.UTF_8);

        putLayout(sample)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))   // 샘플의 version 3 은 무시
                .andExpect(jsonPath("$.unit").value("m"))
                .andExpect(jsonPath("$.floors.length()").value(2))
                .andExpect(jsonPath("$.floors[0].id").value("f-1a2b"))
                .andExpect(jsonPath("$.floors[0].floor.polygon.length()").value(6))
                .andExpect(jsonPath("$.floors[0].tables[0].id").value("t-3f9a"))
                .andExpect(jsonPath("$.floors[0].tables[2].rotation").value(90.0))
                .andExpect(jsonPath("$.floors[0].tables[4].shape").value("ROUND"));

        mvc.perform(get("/api/owner/store/layout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.floors[1].id").value("f-7c3d"))
                .andExpect(jsonPath("$.floors[1].walls.length()").value(0))
                .andExpect(jsonPath("$.floors[0].walls[0].from[0]").value(0.0))
                .andExpect(jsonPath("$.floors[0].chairs.length()").value(12));

        // 샘플 테이블 11개 모두 '손님 없음' 상태가 만들어진다
        assertThat(tableStatusRepository.findByIdStoreId(storeId)).hasSize(11)
                .allMatch(s -> !s.isOccupied());

        // 내 매장 정보의 hasLayout 이 true 로
        mvc.perform(get("/api/owner/store").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasLayout").value(true));
    }

    @Test
    @DisplayName("저장한 적이 없으면 GET 은 404 LAYOUT_NOT_FOUND, 토큰 없으면 401")
    void notFoundAndUnauthorized() throws Exception {
        mvc.perform(get("/api/owner/store/layout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LAYOUT_NOT_FOUND"));

        mvc.perform(get("/api/owner/store/layout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("다시 저장하면 version 이 1 → 2, 비어 있던 크기·회전·모양은 기본값으로 채운다")
    void versionAndDefaults() throws Exception {
        putLayout(layout(floor("f-1", "1층", 0, "t-a")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.floors[0].tables[0].width").value(0.7))
                .andExpect(jsonPath("$.floors[0].tables[0].depth").value(0.7))
                .andExpect(jsonPath("$.floors[0].tables[0].rotation").value(0.0))
                .andExpect(jsonPath("$.floors[0].tables[0].shape").value("RECT"));

        putLayout(layout(floor("f-1", "1층", 0, "t-a")))
                .andExpect(jsonPath("$.version").value(2));
    }

    // ---------- 완료 조건 2: 테이블 1개를 지우고 다시 저장해도 나머지 상태 유지 ----------

    @Test
    @DisplayName("테이블 1개를 지우고 다시 저장하면, 남은 테이블 상태는 유지되고 지운 테이블 상태는 삭제")
    void removeOneTableKeepsOthers() throws Exception {
        putLayout(layout(floor("f-1", "1층", 0, "t-a", "t-b", "t-c"))).andExpect(status().isOk());
        occupy("t-a");
        occupy("t-c");

        // t-b 를 지우고, 새 테이블 t-d 를 추가
        putLayout(layout(floor("f-1", "1층", 0, "t-a", "t-c", "t-d"))).andExpect(status().isOk());

        Map<String, Boolean> statuses = statuses();
        assertThat(statuses).containsOnlyKeys("t-a", "t-c", "t-d");
        assertThat(statuses.get("t-a")).isTrue();    // 유지
        assertThat(statuses.get("t-c")).isTrue();    // 유지
        assertThat(statuses.get("t-d")).isFalse();   // 새 테이블은 손님 없음으로 시작
    }

    // ---------- 완료 조건 3: 한 층만 바꿔 저장해도 다른 층 상태 유지 ----------

    @Test
    @DisplayName("2층만 다시 스캔(새 테이블 id)해서 저장하면 1층 상태는 그대로, 2층은 초기화")
    void rescanOneFloorKeepsOtherFloor() throws Exception {
        putLayout(layout(floor("f-1", "1층", 0, "t-a", "t-b"), floor("f-2", "2층", 1, "t-x", "t-y")))
                .andExpect(status().isOk());
        occupy("t-a");
        occupy("t-x");

        // 2층 테이블만 새 id 로 바뀜
        putLayout(layout(floor("f-1", "1층", 0, "t-a", "t-b"), floor("f-2", "2층", 1, "t-p", "t-q")))
                .andExpect(status().isOk());

        Map<String, Boolean> statuses = statuses();
        assertThat(statuses).containsOnlyKeys("t-a", "t-b", "t-p", "t-q");
        assertThat(statuses.get("t-a")).isTrue();    // 1층은 그대로
        assertThat(statuses.get("t-p")).isFalse();   // 2층은 새로 시작
    }

    @Test
    @DisplayName("층을 삭제하면 그 층 테이블 상태도 함께 삭제")
    void deleteFloorDeletesItsStatuses() throws Exception {
        putLayout(layout(floor("f-1", "1층", 0, "t-a"), floor("f-2", "2층", 1, "t-x")))
                .andExpect(status().isOk());
        occupy("t-a");

        putLayout(layout(floor("f-1", "1층", 0, "t-a"))).andExpect(status().isOk());

        assertThat(statuses()).containsOnlyKeys("t-a");
        assertThat(statuses().get("t-a")).isTrue();
    }

    // ---------- 검증 규칙 → 400 INVALID_LAYOUT ----------

    @Test
    @DisplayName("층 개수(0개, 11개), 층 id·order 중복 → 400 INVALID_LAYOUT")
    void invalidFloors() throws Exception {
        expectInvalid(layout());
        expectInvalid(layout(IntStream.range(0, 11)
                .mapToObj(i -> floor("f-" + i, i + "층", i, "t-" + i)).toArray(String[]::new)));
        expectInvalid(layout(floor("f-1", "1층", 0, "t-a"), floor("f-1", "2층", 1, "t-b")));
        expectInvalid(layout(floor("f-1", "1층", 0, "t-a"), floor("f-2", "2층", 0, "t-b")));
    }

    @Test
    @DisplayName("테이블 개수(0개, 101개), 테이블 id 가 다른 층과 중복 → 400 INVALID_LAYOUT")
    void invalidTableCountAndIds() throws Exception {
        expectInvalid(layout(floor("f-1", "1층", 0)));
        expectInvalid(layout(floor("f-1", "1층", 0,
                IntStream.range(0, 101).mapToObj(i -> "t-" + i).toArray(String[]::new))));
        expectInvalid(layout(floor("f-1", "1층", 0, "t-a"), floor("f-2", "2층", 1, "t-a")));
    }

    @Test
    @DisplayName("좌표 0~100 밖, 크기 0.3~3 밖, 모양이 RECT·ROUND 아님, 이름 21자 → 400 INVALID_LAYOUT")
    void invalidTableValues() throws Exception {
        expectInvalid(layoutWithTable("""
                {"id": "t-a", "name": "1번", "x": 101, "z": 1}"""));
        expectInvalid(layoutWithTable("""
                {"id": "t-a", "name": "1번", "x": 1, "z": 1, "width": 0.2}"""));
        expectInvalid(layoutWithTable("""
                {"id": "t-a", "name": "1번", "x": 1, "z": 1, "depth": 3.5}"""));
        expectInvalid(layoutWithTable("""
                {"id": "t-a", "name": "1번", "x": 1, "z": 1, "shape": "HEX"}"""));
        expectInvalid(layoutWithTable("""
                {"id": "t-a", "name": "%s", "x": 1, "z": 1}""".formatted("가".repeat(21))));
    }

    @Test
    @DisplayName("바닥 선이 교차(8자 모양)하거나 꼭짓점이 3개 미만 → 400 INVALID_LAYOUT")
    void invalidPolygon() throws Exception {
        expectInvalid(layoutWithPolygon("[[0,0],[4,4],[4,0],[0,4]]"));
        expectInvalid(layoutWithPolygon("[[0,0],[4,0]]"));
    }

    @Test
    @DisplayName("검증에 실패하면 아무것도 저장되지 않는다 (기존 배치·상태 그대로)")
    void invalidDoesNotChangeAnything() throws Exception {
        putLayout(layout(floor("f-1", "1층", 0, "t-a"))).andExpect(status().isOk());
        occupy("t-a");

        expectInvalid(layout(floor("f-1", "1층", 0)));   // 테이블 0개 → 실패

        mvc.perform(get("/api/owner/store/layout").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(statuses()).containsOnlyKeys("t-a");
        assertThat(statuses().get("t-a")).isTrue();
    }

    // ---------- 도우미 ----------

    private ResultActions putLayout(String json) throws Exception {
        return mvc.perform(put("/api/owner/store/layout")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void expectInvalid(String json) throws Exception {
        putLayout(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LAYOUT"));
    }

    // 상태 API(#15)가 아직 없으므로 테스트에서는 리포지토리로 직접 '손님 있음' 으로 바꾼다
    private void occupy(String tableId) {
        TableStatus status = tableStatusRepository.findById(new TableStatusId(storeId, tableId)).orElseThrow();
        status.change(true, StatusSource.MANUAL);
    }

    private Map<String, Boolean> statuses() {
        return tableStatusRepository.findByIdStoreId(storeId).stream()
                .collect(Collectors.toMap(TableStatus::getTableId, TableStatus::isOccupied));
    }

    private static String layout(String... floors) {
        return """
                {"version": 99, "unit": "m", "floors": [%s]}""".formatted(String.join(",", floors));
    }

    // 20m × 10m 바닥에 테이블을 한 줄로 놓는다 (좌표가 0~100 안에 들도록 50개마다 줄 바꿈)
    private static String floor(String id, String name, int order, String... tableIds) {
        String tables = IntStream.range(0, tableIds.length)
                .mapToObj(i -> """
                        {"id": "%s", "name": "%d번", "x": %d, "z": %d}"""
                        .formatted(tableIds[i], i + 1, 1 + (i % 50), 1 + (i / 50)))
                .collect(Collectors.joining(","));
        return """
                {"id": "%s", "name": "%s", "order": %d,
                 "floor": {"polygon": [[0,0],[60,0],[60,10],[0,10]]},
                 "walls": [], "tables": [%s], "chairs": []}""".formatted(id, name, order, tables);
    }

    private static String layoutWithTable(String tableJson) {
        return layout("""
                {"id": "f-1", "name": "1층", "order": 0,
                 "floor": {"polygon": [[0,0],[10,0],[10,10],[0,10]]},
                 "tables": [%s]}""".formatted(tableJson));
    }

    private static String layoutWithPolygon(String polygon) {
        return layout("""
                {"id": "f-1", "name": "1층", "order": 0,
                 "floor": {"polygon": %s},
                 "tables": [{"id": "t-a", "name": "1번", "x": 1, "z": 1}]}""".formatted(polygon));
    }
}
