package com.groupeisi.loan.dto;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.*;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class BookResponseDto {
    private Long id;
    private String title;
    private String author;
    private String category;
    private Integer availableStock;
}