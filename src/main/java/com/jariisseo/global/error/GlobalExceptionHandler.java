package com.jariisseo.global.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 컨트롤러·서비스에서 생긴 예외를 API 정의서 7장 형식 { code, message } 으로 바꿔 응답한다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 직접 던진 에러: throw new ApiException(ErrorCode.XXX)
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.getErrorCode(), e.getMessage());
    }

    // @Valid 검증 실패 → 첫 번째 필드 오류를 문구로
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String message = field != null
                ? field.getField() + ": " + field.getDefaultMessage()
                : ErrorCode.INVALID_REQUEST.message();
        return respond(ErrorCode.INVALID_REQUEST, message);
    }

    // JSON 형식 오류, 필수 파라미터 누락, 타입 불일치 (예: 숫자 자리에 문자)
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return respond(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.message());
    }

    // 없는 경로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message());
    }

    // 경로는 있는데 방식이 다름 (예: GET 만 있는 곳에 POST)
    //   405 응답에는 허용되는 방식을 알려주는 Allow 헤더가 있어야 함 (HTTP 규칙) → 스프링이 만든 헤더를 그대로 붙임
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status())
                .headers(e.getHeaders())
                .body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    // 업로드 크기 초과 → 413 (요청 문제이므로 500 이 아님)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "요청 크기가 너무 커요"));
    }

    // 로그인 필요 (SecurityConfig 의 authenticationEntryPoint 가 여기로 넘겨줌)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException e) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.message());
    }

    // 그 밖의 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        // 스프링이 이미 상태 코드를 정해둔 예외 (예: 415 지원하지 않는 Content-Type, 406 등)
        //   → 그 상태 코드와 헤더는 살리고, 본문만 { code, message } 형식으로
        if (e instanceof org.springframework.web.ErrorResponse framework
                && framework.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = framework.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                default -> ErrorCode.INVALID_REQUEST;
            };
            return ResponseEntity.status(status)
                    .headers(framework.getHeaders())
                    .body(ErrorResponse.of(code));
        }

        // 정말 예상하지 못한 서버 오류 → 500. 내부 내용은 응답에 넣지 않고 로그로만 남긴다.
        log.error("처리되지 않은 예외", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, message));
    }
}
