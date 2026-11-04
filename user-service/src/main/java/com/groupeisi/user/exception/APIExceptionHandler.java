package com.groupeisi.user.exception;

import org.springframework.dao.DataIntegrityViolationException;
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
    public ResponseEntity<APIException> n(EntityNotFoundException ex) {
        return b(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<APIException> u(UserAlreadyExistsException ex) {
        return b(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ActiveLoanExistsException.class)
    public ResponseEntity<APIException> a(ActiveLoanExistsException ex) {
        return b(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RequestException.class)
    public ResponseEntity<APIException> c(RequestException ex) {
        return b(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIException> d(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg != null && msg.toLowerCase().contains("email")) {
            return b("User with this email already exists", HttpStatus.CONFLICT);
        }
        return b("Data integrity violation", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIException> v(MethodArgumentNotValidException ex) {
        String e = ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return b(e, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<APIException> b(String m, HttpStatus s) {
        return ResponseEntity.status(s).body(APIException.builder().message(m).status(s.value()).timestamp(LocalDateTime.now()).build());
    }
}
