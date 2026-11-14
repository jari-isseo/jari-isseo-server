package com.jariisseo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// POST /api/auth/signup 요청 (API 정의서 3장)
public record SignupRequest(

        @NotBlank(message = "아이디를 입력해 주세요")
        @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·_ 4~20자예요")
        String loginId,

        // BCrypt 는 72바이트까지만 쓰므로 너무 긴 값은 막는다
        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자예요")
        String password,

        @NotBlank(message = "매장 이름을 입력해 주세요")
        @Size(max = 50, message = "매장 이름은 50자까지예요")
        String storeName
) {
}
