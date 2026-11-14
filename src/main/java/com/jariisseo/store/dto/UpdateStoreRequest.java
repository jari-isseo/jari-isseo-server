package com.jariisseo.store.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH /api/owner/store 요청 (API 정의서 4장)
//   보낸 필드만 수정한다. 빠진 필드(null)는 그대로 둔다.
//   { "name": "카페 문 성수점" }  → 이름만
//   { "open": false }             → 영업 상태만
public record UpdateStoreRequest(

        // null 이면 수정 안 함. 보냈다면 공백만 있는 이름은 안 됨
        //   서비스가 strip() 으로 앞뒤 공백을 지우므로, strip() 과 같은 공백 기준(javaWhitespace)으로 검사
        //   (\\S 는 제어문자 U+001C 같은 것을 글자로 보지만 strip() 은 지워서 빈 이름이 될 수 있음)
        @Pattern(regexp = "(?s).*\\P{javaWhitespace}.*", message = "매장 이름을 입력해 주세요")
        @Size(max = 50, message = "매장 이름은 50자까지예요")
        String name,

        // boolean 이 아닌 Boolean: 안 보냈을 때(null)와 false 를 구분하기 위해
        Boolean open
) {
}
