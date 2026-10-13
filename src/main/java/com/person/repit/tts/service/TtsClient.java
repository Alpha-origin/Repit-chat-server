package com.person.repit.tts.service;

import com.person.repit.tts.domain.TtsInput;
import reactor.core.publisher.Mono;

public interface TtsClient {
    Mono<byte[]> synthesize(TtsInput input);
}
