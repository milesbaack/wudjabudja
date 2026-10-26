package com.wudgabudja.wudjabudja_backend.exception;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(int status, String message, Instant timestamp) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatusException(
            ResponseStatusException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getStatusCode().value(),
                ex.getReason(),
                Instant.now() // UTC Time
        );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(error);
    }
}
