package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.ResolvedSpokenText;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpokenTextResolverTest {
    private SpokenTextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SpokenTextResolver(
                new TtsProperties(),
                new RepitMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void 변환_대상이_없으면_원문을_사용한다() {
        ResolvedSpokenText result = resolver.resolve("자기소개를 해주세요", null);

        assertThat(result.text()).isEqualTo("자기소개를 해주세요");
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.reason()).isEqualTo("NO_CONVERSION_NEEDED");
    }

    @Test
    void 발음_문장이_없으면_원문으로_폴백한다() {
        ResolvedSpokenText result = resolver.resolve("Redis를 설명해주세요", " ");

        assertThat(result.text()).isEqualTo("Redis를 설명해주세요");
        assertThat(result.usedFallback()).isTrue();
        assertThat(result.reason()).isEqualTo("EMPTY");
    }

    @Test
    void 영문이_남으면_원문으로_폴백한다() {
        ResolvedSpokenText result = resolver.resolve(
                "Redis를 설명해주세요", "Redis를 설명해주세요"
        );

        assertThat(result.reason()).isEqualTo("LATIN_REMAINS");
    }

    @Test
    void 태그_문자가_있으면_원문으로_폴백한다() {
        ResolvedSpokenText result = resolver.resolve(
                "Redis를 설명해주세요", "[레디스]를 설명해주세요"
        );

        assertThat(result.reason()).isEqualTo("TAG_CHARS");
    }

    @Test
    void 길이_비율이_범위를_벗어나면_원문으로_폴백한다() {
        ResolvedSpokenText result = resolver.resolve("API 1234567890", "에이피아이");

        assertThat(result.reason()).isEqualTo("LENGTH_RATIO");
    }

    @Test
    void 유효한_발음_문장을_사용한다() {
        ResolvedSpokenText result = resolver.resolve(
                "Redis와 JWT를 설명해주세요",
                "레디스와 제이더블유티를 설명해주세요"
        );

        assertThat(result.text()).isEqualTo("레디스와 제이더블유티를 설명해주세요");
        assertThat(result.usedFallback()).isFalse();
        assertThat(result.reason()).isEqualTo("OK");
    }
}
