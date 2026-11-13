package com.jariisseo.global.error;

import org.springframework.http.HttpStatus;

// API 정의서 7장 에러 코드. message 는 사용자에게 그대로 보여줄 수 있는 한국어 문장.
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요"),
    INVALID_LAYOUT(HttpStatus.BAD_REQUEST, "배치 정보가 올바르지 않아요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 맞지 않아요"),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없어요"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "테이블을 찾을 수 없어요"),
    LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),

    // 7장에 없던 공통 코드 (#10에서 추가)
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
