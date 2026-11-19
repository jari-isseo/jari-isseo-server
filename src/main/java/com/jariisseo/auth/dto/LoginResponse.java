package com.jariisseo.auth.dto;

// 200 OK: { "accessToken": "eyJ...", "expiresIn": 2592000 }
public record LoginResponse(String accessToken, long expiresIn) {
}
