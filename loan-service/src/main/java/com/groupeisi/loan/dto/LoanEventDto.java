package com.groupeisi.loan.dto;

import lombok.*;

@Getter
@Setter
public class LoanEventDto {
    /** Identifiant outbox (idempotence côté catalog-service). */
    private Long eventId;
    private Long bookId;
}
