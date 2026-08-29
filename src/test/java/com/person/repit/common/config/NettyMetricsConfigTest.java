package com.person.repit.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NettyMetricsConfigTest {

    @Test
    void 세션_ID를_포함한_면접_URI를_템플릿으로_변환한다() {
        assertThat(NettyMetricsConfig.normalizeUri("/chat/interviews/session-123"))
                .isEqualTo("/chat/interviews/{sessionId}");
    }

    @Test
    void 세션_ID와_동작을_포함한_면접_URI를_템플릿으로_변환한다() {
        assertThat(NettyMetricsConfig.normalizeUri("/chat/interviews/session-123/answers"))
                .isEqualTo("/chat/interviews/{sessionId}/answers");
        assertThat(NettyMetricsConfig.normalizeUri("/chat/interviews/session-123/complete"))
                .isEqualTo("/chat/interviews/{sessionId}/complete");
    }

    @Test
    void 쿼리_문자열을_지표_URI에서_제거한다() {
        assertThat(NettyMetricsConfig.normalizeUri("/chat/interviews?type=technical"))
                .isEqualTo("/chat/interviews");
    }

    @Test
    void 알_수_없는_URI는_하나의_태그로_묶는다() {
        assertThat(NettyMetricsConfig.normalizeUri("/unknown/12345"))
                .isEqualTo("/other");
    }
}
