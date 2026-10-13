package com.person.repit.tts.config;

import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties("tts")
public class TtsProperties {
    private boolean enabled;
    private int defaultVoiceIndex = 1;
    private String profileVersion = "persona-tts-v1";
    private SpokenText spokenText = new SpokenText();
    private ElevenLabs elevenlabs = new ElevenLabs();
    private Map<Integer, String> voices = new HashMap<>();
    private Defaults defaults = new Defaults();
    private Presets presets = new Presets();
    private Clamp clamp = new Clamp();
    private Pause pause = new Pause();
    private Queue queue = new Queue();
    private Retry retry = new Retry();
    private LongPoll longPoll = new LongPoll();
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class SpokenText {
        private double minLengthRatio = 0.5;
        private double maxLengthRatio = 2.5;
    }

    @Getter
    @Setter
    public static class ElevenLabs {
        private String baseUrl = "https://api.elevenlabs.io";
        private String apiKey = "";
        private String modelId = "eleven_multilingual_v2";
        private String outputFormat = "mp3_44100_128";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration responseTimeout = Duration.ofSeconds(20);
        private Pool pool = new Pool();
    }

    @Getter
    @Setter
    public static class Pool {
        private int maxConnections = 8;
        private int pendingAcquireMaxCount = 16;
    }

    @Getter
    @Setter
    public static class Defaults {
        private double stability = 0.55;
        private double similarityBoost = 0.8;
        private double style = 0.1;
        private double speed = 1.1;
        private boolean useSpeakerBoost = true;
    }

    @Getter
    @Setter
    public static class Presets {
        private Map<InterviewMode, Map<InterviewTone, Preset>> base = new EnumMap<>(InterviewMode.class);
        private Map<InterviewTone, Preset> toneAdjust = new EnumMap<>(InterviewTone.class);
        private Map<InterviewType, Preset> typeAdjust = new EnumMap<>(InterviewType.class);
    }

    @Getter
    @Setter
    public static class Preset {
        private double stability;
        private double style;
        private double speed;
    }

    @Getter
    @Setter
    public static class Clamp {
        private double speedMin = 0.8;
        private double speedMax = 1.2;
    }

    @Getter
    @Setter
    public static class Pause {
        private boolean enabled;
        private int maxPerQuestion = 2;
    }

    @Getter
    @Setter
    public static class Queue {
        private int globalMaxConcurrency = 4;
        private int prefetchMaxConcurrency = 3;
        private int perSessionMax = 2;
        private int perSessionPrefetchMax = 1;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxRetries = 2;
        private List<Duration> backoffs = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2));
        private int uploadRetries = 2;
    }

    @Getter
    @Setter
    public static class LongPoll {
        private Duration defaultWait = Duration.ofSeconds(20);
        private Duration maxWait = Duration.ofSeconds(25);
    }

    @Getter
    @Setter
    public static class Storage {
        private String bucket = "";
        private String region = "ap-northeast-2";
        private String publicBaseUrl = "";
        private String keyPrefix = "tts";
        private String cacheControl = "public, max-age=3600";
        private String accessKey = "";
        private String secretKey = "";
    }
}
