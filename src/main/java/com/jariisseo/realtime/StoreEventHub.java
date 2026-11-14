package com.jariisseo.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

// 매장별 SSE 구독자 목록 (API 정의서 5장)
//   storeId → 그 매장 손님 화면들의 연결(SseEmitter) 목록
//   - 연결 직후 snapshot 전송
//   - 20초마다 ": ping" 주석 → Nginx·통신사가 조용한 연결을 끊지 않도록
//   - 30분 타임아웃 → 서버가 연결을 닫고, 브라우저 EventSource 가 자동 재연결 (재연결하면 snapshot 을 다시 받음)
//   - 연결 끊김·타임아웃·전송 실패 시 목록에서 제거 → 닫힌 연결이 쌓여 메모리가 새지 않도록
@Component
public class StoreEventHub {

    private static final Logger log = LoggerFactory.getLogger(StoreEventHub.class);

    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final long HEARTBEAT_MILLIS = 20_000;

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    // 구독 시작: 목록에 먼저 넣고 → 스냅샷을 읽어 보냄
    //   순서가 반대면 '스냅샷을 읽은 뒤 ~ 목록에 들어가기 전' 사이의 변경을 놓칠 수 있다.
    //   목록에 먼저 넣으면 그 사이 변경은 스냅샷에 들어 있거나, 뒤이어 이벤트로 온다.
    public SseEmitter subscribe(Long storeId, Supplier<?> snapshot) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());

        emitter.onCompletion(() -> remove(storeId, emitter));
        emitter.onTimeout(() -> {
            remove(storeId, emitter);
            emitter.complete();   // 연결을 정상 종료 → 브라우저가 다시 연결
        });
        emitter.onError(e -> remove(storeId, emitter));

        // 스냅샷 전송이 끝나기 전에 다른 이벤트가 먼저 나가지 않도록 같은 연결에 대한 전송은 하나씩
        synchronized (emitter) {
            // 추가·제거를 매장 단위로 한 번에 처리 → 동시에 마지막 구독자가 빠져도 새 구독이 사라지지 않음
            subscribers.compute(storeId, (id, emitters) -> {
                Set<SseEmitter> set = emitters != null ? emitters : new CopyOnWriteArraySet<>();
                set.add(emitter);
                return set;
            });
            try {
                send(storeId, emitter, SseEmitter.event().name("snapshot").data(snapshot.get(), MediaType.APPLICATION_JSON));
            } catch (RuntimeException e) {
                remove(storeId, emitter);   // 스냅샷을 못 읽으면(DB 오류 등) 목록에 남기지 않고 에러 응답으로
                throw e;
            }
        }
        return emitter;
    }

    // 매장의 모든 손님 화면에 이벤트 전송 (status·layout·store·snapshot). 변경 이벤트 발행은 #19 에서 연결
    public void broadcast(Long storeId, String eventName, Object data) {
        for (SseEmitter emitter : subscribers.getOrDefault(storeId, Set.of())) {
            synchronized (emitter) {
                send(storeId, emitter, SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
            }
        }
    }

    // 하트비트: 모든 연결에 주석 한 줄. 이미 끊긴 연결은 여기서 전송이 실패해 목록에서 빠진다
    @Scheduled(initialDelay = HEARTBEAT_MILLIS, fixedRate = HEARTBEAT_MILLIS)
    public void sendHeartbeat() {
        subscribers.forEach((storeId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                synchronized (emitter) {
                    send(storeId, emitter, SseEmitter.event().comment("ping"));
                }
            }
        });
    }

    public int subscriberCount(Long storeId) {
        return subscribers.getOrDefault(storeId, Set.of()).size();
    }

    private void send(Long storeId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            // 손님이 페이지를 닫은 경우가 대부분 → 오류가 아니므로 debug 로만 남김
            log.debug("SSE 전송 실패, 구독 해제 (storeId={})", storeId, e);
            remove(storeId, emitter);
        }
    }

    private void remove(Long storeId, SseEmitter emitter) {
        // 마지막 구독자가 빠지면 매장 항목도 지움 (빈 목록이 계속 쌓이지 않도록)
        subscribers.computeIfPresent(storeId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
