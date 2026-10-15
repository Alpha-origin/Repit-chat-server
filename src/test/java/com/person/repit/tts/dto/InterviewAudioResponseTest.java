package com.person.repit.tts.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewAudioResponseTest {
    @ParameterizedTest
    @EnumSource(AudioStatus.class)
    void 모든_내부_상태를_외부_세_상태로_직렬화한다(AudioStatus status) throws Exception {
        var response = InterviewAudioResponse.from(AudioState.builder()
                .questionId(1L).status(status).url("https://audio.example/1.mp3").build());
        String expected = switch (status) {
            case READY -> "READY";
            case FAILED -> "FAILED";
            case QUEUED, GENERATING, PENDING -> "PENDING";
        };
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(response));
        assertThat(json.get("status").asText()).isEqualTo(expected);
        assertThat(json.get("questionId").asLong()).isEqualTo(1L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void 조회할_URL이_없으면_READY를_반환하지_않는다(String url) {
        var response = InterviewAudioResponse.from(AudioState.builder()
                .questionId(1L).status(AudioStatus.READY).url(url).build());
        assertThat(response.status()).isEqualTo(AudioResponseStatus.PENDING);
    }
}
