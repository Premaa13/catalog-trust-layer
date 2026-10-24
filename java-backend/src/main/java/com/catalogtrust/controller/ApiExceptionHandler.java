package com.catalogtrust.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.catalogtrust.service.ProductNotFoundException;

import java.io.IOException;
import java.util.Map;

/**
 * Returns API errors in a small JSON shape compatible with FastAPI error responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "detail", java.util.List.of(Map.of(
                        "type", "json_invalid",
                        "loc", java.util.List.of("body"),
                        "msg", "Invalid JSON",
                        "input", ""
                ))
        ));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProductNotFound(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("detail", "product_id not found"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleFileError(IOException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", exception.getMessage()));
    }
}
