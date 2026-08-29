package com.person.repit.common.config;

import com.person.repit.interview.websocket.ReactiveChatInterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class ReactiveWebSocketConfig {

    private static final String CHAT_INTERVIEW_PATH = "/ws/chat/interviews";

    private final ReactiveChatInterviewWebSocketHandler chatInterviewWebSocketHandler;

    @Bean
    public HandlerMapping reactiveWebSocketHandlerMapping() {
        Map<String, WebSocketHandler> handlers = Map.of(
                CHAT_INTERVIEW_PATH,
                chatInterviewWebSocketHandler
        );

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping(handlers, -1);
        mapping.setCorsConfigurations(Map.of(
                CHAT_INTERVIEW_PATH,
                webSocketCorsConfiguration()
        ));
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    private CorsConfiguration webSocketCorsConfiguration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET"));
        configuration.setAllowedHeaders(List.of("*"));
        return configuration;
    }
}
