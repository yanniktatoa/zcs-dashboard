package com.groupeisi.catalog.mapping;

import com.groupeisi.catalog.dto.BookRequestDto;
import com.groupeisi.catalog.dto.BookResponseDto;
import com.groupeisi.catalog.entities.BookEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookMapper {
    @Mapping(target = "id", ignore = true)
    BookEntity toEntity(BookRequestDto dto);
    BookResponseDto toDto(BookEntity entity);
}
