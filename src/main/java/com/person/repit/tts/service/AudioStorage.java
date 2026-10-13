package com.person.repit.tts.service;

import com.person.repit.tts.domain.StoredAudio;
import reactor.core.publisher.Mono;

public interface AudioStorage {
    Mono<StoredAudio> put(String sessionId, Long questionId, String inputHash, byte[] audio);
}
