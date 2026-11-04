package com.groupeisi.loan.exception;

import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@ControllerAdvice
public class APIExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<APIException> n(EntityNotFoundException ex) {
        return b(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RequestException.class)
    public ResponseEntity<APIException> c(RequestException ex) {
        return b(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIException> v(MethodArgumentNotValidException ex) {
        String e = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return b(e, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<APIException> b(String m, HttpStatus s) {
        return ResponseEntity.status(s)
                .body(APIException.builder().message(m).status(s.value()).timestamp(LocalDateTime.now()).build());
    }
}
