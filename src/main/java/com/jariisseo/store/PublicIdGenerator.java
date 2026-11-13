package com.jariisseo.store;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// 손님 링크용 publicId 만들기: 8자리 랜덤 (예: k3x9q2mf)
//   - 헷갈리는 글자(0·o, 1·l)는 뺀 소문자+숫자 32종 → 32^8 ≈ 1조 가지
//   - 이미 있는 값이면 다시 뽑는다 (DB UNIQUE 제약이 마지막 안전장치)
@Component
public class PublicIdGenerator {

    static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";
    static final int LENGTH = 8;
    private static final int MAX_TRIES = 10;

    private final SecureRandom random = new SecureRandom();
    private final StoreRepository storeRepository;

    public PublicIdGenerator(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public String generate() {
        for (int i = 0; i < MAX_TRIES; i++) {
            String candidate = randomId();
            if (!storeRepository.existsByPublicId(candidate)) {
                return candidate;
            }
        }
        // 1조 가지 중 10번 연속 충돌은 사실상 불가능 → 생기면 버그이므로 500
        throw new IllegalStateException("publicId 를 만들지 못했어요");
    }

    String randomId() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
