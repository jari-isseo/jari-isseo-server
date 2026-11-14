package com.jariisseo.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// API 로는 일부러 만들기 어려운 경우를 직접 확인 (#17)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("예상하지 못한 예외 → 500 INTERNAL_ERROR, 내부 내용(예외 메시지)은 응답에 넣지 않는다")
    void unexpectedExceptionHidesDetails() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnknown(new IllegalStateException("DB 비밀번호: 1234"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INTERNAL_ERROR.message())
                .doesNotContain("1234");
    }

    @Test
    @DisplayName("ApiException → 그 에러 코드의 상태와 { code, message }")
    void apiException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApi(new ApiException(ErrorCode.TABLE_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("TABLE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("테이블을 찾을 수 없어요");
    }
}
