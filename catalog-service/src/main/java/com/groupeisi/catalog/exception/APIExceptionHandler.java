package com.groupeisi.catalog.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@ControllerAdvice
public class APIExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<APIException> handleNotFound(EntityNotFoundException ex) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConcurrentStockUpdateException.class)
    public ResponseEntity<APIException> handleConcurrentStock(ConcurrentStockUpdateException ex) {
        return build(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RequestException.class)
    public ResponseEntity<APIException> handleConflict(RequestException ex) {
        return build(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIException> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return build(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIException> handleOther(Exception ex) {
        return build(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<APIException> build(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(APIException.builder()
                .message(message)
                .status(status.value())
                .timestamp(LocalDateTime.now())
                .build());
    }
}
