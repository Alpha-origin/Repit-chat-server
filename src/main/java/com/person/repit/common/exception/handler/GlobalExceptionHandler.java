package com.person.repit.common.exception.handler;

import com.person.repit.common.exception.AlreadyUsingException;
import com.person.repit.common.exception.AuthenticationException;
import com.person.repit.common.exception.BusinessException;
import com.person.repit.common.exception.InvalidRequestException;
import com.person.repit.common.exception.NotFoundException;
import com.person.repit.common.exception.ServerLogicException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebInputException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            WebExchangeBindException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String rejectedValue = error.getRejectedValue() == null ? "null" : "provided";

            log.warn(
                    "[REQUEST VALIDATION FAILED] field={}, value={}, reason={}",
                    error.getField(),
                    rejectedValue,
                    error.getDefaultMessage()
            );

            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        });

        Map<String, Object> response = responseBody(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "요청 값이 올바르지 않습니다."
        );
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleWebInputException(
            ServerWebInputException exception
    ) {
        log.warn("[INVALID REQUEST FORMAT] {}", exception.getReason());
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_FORMAT", "요청 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequestException(
            InvalidRequestException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundException(
            NotFoundException exception
    ) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AlreadyUsingException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyUsingException(
            AlreadyUsingException exception
    ) {
        return error(HttpStatus.CONFLICT, "ALREADY_IN_USE", exception.getMessage());
    }

    @ExceptionHandler(ServerLogicException.class)
    public ResponseEntity<Map<String, Object>> handleServerLogicException(
            ServerLogicException exception
    ) {
        log.error("[SERVER LOGIC ERROR]", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", exception.getMessage());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleWebClientResponseException(
            WebClientResponseException exception
    ) {
        log.error("[UPSTREAM SERVER ERROR] status={}", exception.getStatusCode().value(), exception);
        return error(HttpStatus.BAD_GATEWAY, "UPSTREAM_SERVER_ERROR", "외부 서버 요청 처리에 실패했습니다.");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(
            BusinessException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        log.error("[UNEXPECTED ERROR]", exception);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다."
        );
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(responseBody(status, code, message));
    }

    private Map<String, Object> responseBody(
            HttpStatus status,
            String code,
            String message
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status.value());
        response.put("code", code);
        response.put("message", message == null || message.isBlank()
                ? "요청 처리 중 오류가 발생했습니다."
                : message);
        return response;
    }
}
