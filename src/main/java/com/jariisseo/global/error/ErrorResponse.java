package com.jariisseo.global.error;

// 모든 에러 응답의 모양: { "code": "TABLE_NOT_FOUND", "message": "테이블을 찾을 수 없어요" }
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.message());
    }

    // 기본 문구 대신 상황에 맞는 문구를 쓸 때 (예: "name 은(는) 필수예요")
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
