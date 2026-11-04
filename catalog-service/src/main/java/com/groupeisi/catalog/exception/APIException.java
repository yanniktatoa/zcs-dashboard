package com.groupeisi.catalog.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class APIException {
    private String message;
    private int status;
    private LocalDateTime timestamp;
}
