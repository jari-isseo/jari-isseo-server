package com.jariisseo.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// API 정의서 7장 에러 코드 8개가 정의서의 HTTP 상태와 일치하는지 (#17)
//   누가 ErrorCode 를 고쳐서 상태가 바뀌면 이 테스트가 깨져서 바로 알 수 있다
class ErrorCodeTest {

    private static final Map<ErrorCode, HttpStatus> SPEC = Map.of(
            ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
            ErrorCode.INVALID_LAYOUT, HttpStatus.BAD_REQUEST,
            ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
            ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED,
            ErrorCode.STORE_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.TABLE_NOT_FOUND, HttpStatus.NOT_FOUND,
            ErrorCode.LOGIN_ID_TAKEN, HttpStatus.CONFLICT,
            ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR
    );

    @Test
    @DisplayName("정의서 7장 에러 코드 8개의 HTTP 상태가 정의서와 같다")
    void statusesMatchSpec() {
        SPEC.forEach((code, status) ->
                assertThat(code.status()).as(code.name()).isEqualTo(status));
    }

    @Test
    @DisplayName("모든 에러 코드는 사용자에게 보여줄 한국어 문구를 가진다")
    void everyCodeHasMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.message()).as(code.name()).isNotBlank();
        }
    }
}
