package com.jariisseo.realtime;

import com.jariisseo.global.config.SwaggerTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 손님 실시간 구독 SSE (API 정의서 5장) — 인증 없음
@Tag(name = SwaggerTags.PUBLIC)
@RestController
@RequestMapping("/api/public/stores")
public class PublicEventController {

    private final StoreEventService eventService;

    public PublicEventController(StoreEventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "실시간 구독 (SSE)",
            description = "연결 직후 snapshot, 이후 변경 이벤트(status·layout·store). 20초마다 :ping, 30분 후 재연결. "
                    + "없는 publicId 면 404 STORE_NOT_FOUND. Swagger 에서는 응답이 끝나지 않으니 curl -N 으로 확인")
    @GetMapping(value = "/{publicId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String publicId, HttpServletResponse response) {
        // 배포 시 Nginx 가 응답을 모아서 보내지 않고 바로바로 넘기도록
        response.setHeader("X-Accel-Buffering", "no");
        return eventService.subscribe(publicId);
    }
}
