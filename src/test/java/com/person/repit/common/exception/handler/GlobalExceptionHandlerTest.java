package com.person.repit.common.exception.handler;

import com.person.repit.interview.exception.InterviewSessionNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

class GlobalExceptionHandlerTest {

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(new TestController())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 검증_실패_메세지와_필드_오류를_반환한다() {
        webTestClient.post()
                .uri("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.message").isEqualTo("요청 값이 올바르지 않습니다.")
                .jsonPath("$.errors.name").isEqualTo("이름이 필요합니다.");
    }

    @Test
    void 잘못된_JSON에_오류_메세지를_반환한다() {
        webTestClient.post()
                .uri("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST_FORMAT")
                .jsonPath("$.message").isEqualTo("요청 형식이 올바르지 않습니다.");
    }

    @Test
    void 존재하지_않는_리소스의_메세지를_반환한다() {
        webTestClient.get()
                .uri("/test/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.code").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("면접 세션을 찾을 수 없습니다.");
    }

    @Test
    void 예상하지_못한_오류는_안전한_메세지를_반환한다() {
        webTestClient.get()
                .uri("/test/error")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.code").isEqualTo("INTERNAL_SERVER_ERROR")
                .jsonPath("$.message").isEqualTo("서버 내부 오류가 발생했습니다.");
    }

    @RestController
    private static class TestController {

        @PostMapping("/test/validation")
        Mono<Void> validate(@Valid @RequestBody TestRequest request) {
            return Mono.empty();
        }

        @GetMapping("/test/not-found")
        Mono<Void> notFound() {
            return Mono.error(new InterviewSessionNotFoundException("면접 세션을 찾을 수 없습니다."));
        }

        @GetMapping("/test/error")
        Mono<Void> error() {
            return Mono.error(new IllegalStateException("노출되면 안 되는 내부 오류"));
        }
    }

    private record TestRequest(
            @NotBlank(message = "이름이 필요합니다.") String name
    ) {
    }
}
