package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsInput;
import com.person.repit.tts.domain.TtsProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsInputBuilderTest {

    @Test
    void 기본값에서는_문장_사이_쉼을_넣지_않는다() {
        TtsProperties properties = new TtsProperties();
        TtsInputBuilder builder = builder(properties);

        TtsInput result = builder.build(question(), profile(500));

        assertThat(result.text()).isEqualTo("레디스를 설명해주세요. 장점은 무엇인가요?");
        assertThat(result.text()).doesNotContain("<break");
        assertThat(result.inputHash()).hasSize(16);
    }

    @Test
    void 쉼을_켜면_최대_두_번만_추가한다() {
        TtsProperties properties = new TtsProperties();
        properties.getPause().setEnabled(true);
        properties.getPause().setMaxPerQuestion(2);
        TtsInputBuilder builder = builder(properties);
        ChatQuestion question = ChatQuestion.builder()
                .content("Redis. API? JVM! 끝")
                .spokenText("레디스. 에이피아이? 제이브이엠! 끝")
                .build();

        TtsInput result = builder.build(question, profile(500));

        assertThat(result.text()).contains("<break time=\"0.5s\" />");
        assertThat(result.text().split("<break", -1)).hasSize(3);
    }

    private TtsInputBuilder builder(TtsProperties properties) {
        SpokenTextResolver resolver = new SpokenTextResolver(
                properties, new RepitMetrics(new SimpleMeterRegistry())
        );
        return new TtsInputBuilder(resolver, properties);
    }

    private ChatQuestion question() {
        return ChatQuestion.builder()
                .content("Redis를 설명해주세요. 장점은 무엇인가요?")
                .spokenText("레디스를 설명해주세요. 장점은 무엇인가요?")
                .build();
    }

    private TtsProfile profile(int pauseMs) {
        return TtsProfile.builder()
                .voiceId("voice")
                .modelId("model")
                .outputFormat("mp3")
                .stability(0.5)
                .similarityBoost(0.8)
                .style(0.1)
                .speed(1.0)
                .useSpeakerBoost(true)
                .pauseAfterSentenceMs(pauseMs)
                .profileVersion("v1")
                .build();
    }
}
