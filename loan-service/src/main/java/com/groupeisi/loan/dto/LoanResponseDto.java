package com.groupeisi.loan.dto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.*;
import java.time.LocalDate;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class LoanResponseDto {
    private Long id;
    private Long userId;
    private Long bookId;
    private LocalDate loanDate;
    private LocalDate returnDate;
    private String status;
}