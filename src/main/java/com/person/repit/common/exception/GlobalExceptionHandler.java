package com.person.repit.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException exception
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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("message", "요청 값이 올바르지 않습니다.");
        response.put("errors", errors);

        return ResponseEntity.badRequest().body(response);
    }
}
