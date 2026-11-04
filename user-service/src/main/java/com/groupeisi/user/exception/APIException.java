package com.groupeisi.user.exception;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class APIException {
    private String message;
    private int status;
    private LocalDateTime timestamp;
}
