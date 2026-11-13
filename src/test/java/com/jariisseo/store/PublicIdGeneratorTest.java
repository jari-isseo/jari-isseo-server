package com.jariisseo.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DB 없이 빠르게 도는 단위 테스트. StoreRepository 는 가짜(mock)로 바꿔 충돌 상황을 흉내 낸다.
class PublicIdGeneratorTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PublicIdGenerator generator = new PublicIdGenerator(storeRepository);

    @Test
    @DisplayName("8자리, 헷갈리는 글자(0·o·1·l) 없이 만든다")
    void eightCharsWithoutConfusingLetters() {
        when(storeRepository.existsByPublicId(anyString())).thenReturn(false);

        String id = generator.generate();

        assertThat(id).hasSize(8).matches("[a-kmnp-z2-9]{8}");
    }

    @Test
    @DisplayName("이미 있는 값이면 다시 뽑는다")
    void retryOnCollision() {
        // 처음 두 번은 '이미 있음', 세 번째에 '없음'
        when(storeRepository.existsByPublicId(anyString())).thenReturn(true, true, false);

        generator.generate();

        verify(storeRepository, times(3)).existsByPublicId(anyString());
    }

    @Test
    @DisplayName("계속 충돌하면 무한히 돌지 않고 멈춘다")
    void giveUpAfterMaxTries() {
        when(storeRepository.existsByPublicId(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
    }
}
