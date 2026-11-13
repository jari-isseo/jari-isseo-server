package com.jariisseo.global.error;

// 서비스 코드에서 에러를 낼 때 사용.
//   throw new ApiException(ErrorCode.TABLE_NOT_FOUND);
// → GlobalExceptionHandler 가 받아서 404 + { code, message } 로 응답한다.
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
