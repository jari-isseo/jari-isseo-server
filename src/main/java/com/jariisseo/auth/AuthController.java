package com.jariisseo.auth;

import com.jariisseo.auth.dto.LoginRequest;
import com.jariisseo.auth.dto.LoginResponse;
import com.jariisseo.auth.dto.SignupRequest;
import com.jariisseo.auth.dto.SignupResponse;
import com.jariisseo.global.config.SwaggerTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = SwaggerTags.AUTH)
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "가입 (매장 동시 생성)",
            description = "아이디 중복이면 409 LOGIN_ID_TAKEN. 응답의 accessToken 으로 바로 사장님 API 사용 가능")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @Operation(summary = "로그인", description = "토큰 유효기간 30일. 실패 시 401 INVALID_CREDENTIALS")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
