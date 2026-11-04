package com.groupeisi.catalog.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookResponseDto {
    private Long id;
    private String title;
    private String author;
    private String category;
    private Integer availableStock;
    private Long version;
}
