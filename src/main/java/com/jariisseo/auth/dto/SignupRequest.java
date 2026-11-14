package com.jariisseo.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

// POST /api/auth/signup 요청 (API 정의서 3장)
public record SignupRequest(

        @NotBlank(message = "아이디를 입력해 주세요")
        @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·_ 4~20자예요")
        String loginId,

        // 글자 수 제한. 바이트 제한(BCrypt 72바이트)은 아래 isPasswordWithinBcryptLimit
        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자예요")
        String password,

        // @NotBlank 는 trim() 기준이라 전각 공백(U+2003) 같은 건 글자로 봄 → strip() 기준으로 한 번 더 (#13)
        @NotBlank(message = "매장 이름을 입력해 주세요")
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "매장 이름을 입력해 주세요")
        @Size(max = 50, message = "매장 이름은 50자까지예요")
        String storeName
) {

    // BCrypt 는 72바이트까지만 처리하고, 넘으면 encode() 가 예외를 던져 500 이 된다.
    // 한글은 1글자 = 3바이트라 64자 제한만으로는 부족 → 바이트로 한 번 더 검사해서 400 으로
    @JsonIgnore   // JSON·Swagger 항목으로 보이지 않게 (검사용 메서드)
    @AssertTrue(message = "비밀번호가 너무 길어요")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
