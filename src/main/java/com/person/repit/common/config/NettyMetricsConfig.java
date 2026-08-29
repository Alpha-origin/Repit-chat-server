package com.person.repit.common.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.regex.Pattern;

@Configuration
public class NettyMetricsConfig {

    private static final String INTERVIEWS_PATH = "/chat/interviews";
    private static final Pattern INTERVIEW_ACTION_PATH = Pattern.compile(
            "^/chat/interviews/[^/]+/(question|answers|complete|quit)$"
    );
    private static final Pattern INTERVIEW_SESSION_PATH = Pattern.compile(
            "^/chat/interviews/[^/]+$"
    );
    private static final Set<String> FIXED_PATHS = Set.of(
            "/", INTERVIEWS_PATH, "/ws/chat/interviews", "/error"
    );

    @Bean
    WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyMetricsCustomizer() {
        return factory -> factory.addServerCustomizers(
                httpServer -> httpServer.metrics(true, NettyMetricsConfig::normalizeUri)
        );
    }

    @Bean
    MeterFilter nettyUriTagLimit() {
        return MeterFilter.maximumAllowableTags(
                "reactor.netty.http.server", "uri", 100, MeterFilter.deny()
        );
    }

    static String normalizeUri(String uri) {
        String path = uri.split("\\?", 2)[0];

        if (FIXED_PATHS.contains(path)) {
            return path;
        }
        if (INTERVIEW_ACTION_PATH.matcher(path).matches()) {
            return INTERVIEW_ACTION_PATH.matcher(path)
                    .replaceFirst(INTERVIEWS_PATH + "/{sessionId}/$1");
        }
        if (INTERVIEW_SESSION_PATH.matcher(path).matches()) {
            return INTERVIEWS_PATH + "/{sessionId}";
        }
        if (path.startsWith("/actuator")) {
            return "/actuator/**";
        }
        if (path.startsWith("/v3/api-docs")) {
            return "/v3/api-docs/**";
        }
        if (path.startsWith("/swagger-ui")) {
            return "/swagger-ui/**";
        }
        return "/other";
    }
}
