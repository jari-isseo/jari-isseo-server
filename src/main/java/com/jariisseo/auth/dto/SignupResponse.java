package com.jariisseo.auth.dto;

// 201 Created: { "accessToken": "eyJ...", "store": { "publicId": "k3x9q2mf", "name": "카페 문" } }
public record SignupResponse(String accessToken, StoreSummary store) {

    public record StoreSummary(String publicId, String name) {
    }
}
