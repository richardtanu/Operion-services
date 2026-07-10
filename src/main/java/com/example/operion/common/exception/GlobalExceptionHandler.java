package com.example.operion.common.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {

                Map<String, Object> response = new HashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("error", ex.getMessage());

                return ResponseEntity.badRequest().body(response);
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {

                Map<String, Object> response = new HashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("error", "Invalid username or password");

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleGeneralException(Exception ex) {
                ex.printStackTrace();

                Map<String, Object> response = new HashMap<>();

                response.put("timestamp", LocalDateTime.now());
                response.put("error", "Internal server error");
                response.put("desc", ex.getMessage());

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(response);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<?> handleValidationException(
                        MethodArgumentNotValidException ex) {

                Map<String, String> errors = new HashMap<>();

                ex.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> errors.put(
                                                error.getField(),
                                                error.getDefaultMessage()));

                Map<String, Object> response = new HashMap<>();

                response.put(
                                "timestamp",
                                LocalDateTime.now());

                response.put(
                                "error",
                                "Validation failed");

                response.put(
                                "details",
                                errors);

                return ResponseEntity
                                .badRequest()
                                .body(response);
        }
}
