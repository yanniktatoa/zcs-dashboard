package com.groupeisi.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ActiveLoanExistsException extends RuntimeException {

    public ActiveLoanExistsException(String message) {
        super(message);
    }
}
