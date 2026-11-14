package com.jariisseo.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 바닥 다각형 자기 교차 검사 (DB·스프링 없이 도는 단위 테스트)
class LayoutValidatorTest {

    @Test
    @DisplayName("오목한 ㄱ자 바닥, 사각형, 삼각형은 통과")
    void simplePolygons() {
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 8, 0, 8, 3.5, 5.5, 3.5, 5.5, 6, 0, 6))).isTrue();
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 7, 0, 7, 5, 0, 5))).isTrue();
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 4, 0, 0, 3))).isTrue();
        // 한 변 중간에 꼭짓점이 하나 더 있어도 괜찮음
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 2, 0, 4, 0, 4, 4, 0, 4))).isTrue();
    }

    @Test
    @DisplayName("8자(나비넥타이)처럼 선이 교차하면 거부")
    void bowTie() {
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 4, 4, 4, 0, 0, 4))).isFalse();
    }

    @Test
    @DisplayName("점이 한 줄 위에만 있거나, 같은 점이 연속되거나, 꼭짓점끼리 닿으면 거부")
    void degenerate() {
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 1, 0, 2, 0))).isFalse();
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 4, 0, 4, 0, 4, 4, 0, 4))).isFalse();
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 4, 0, 2, 2, 4, 4, 0, 4, 2, 2))).isFalse();
    }

    @Test
    @DisplayName("변이 되돌아가며 겹치는 바늘 모양은 거부")
    void spike() {
        assertThat(LayoutValidator.isSimplePolygon(points(0, 0, 4, 0, 4, 4, 4, 2, 0, 4))).isFalse();
    }

    private static List<double[]> points(double... xz) {
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i < xz.length; i += 2) {
            list.add(new double[]{xz[i], xz[i + 1]});
        }
        return list;
    }
}
