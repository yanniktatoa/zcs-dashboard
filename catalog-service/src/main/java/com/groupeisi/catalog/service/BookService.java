package com.groupeisi.catalog.service;

import com.groupeisi.catalog.dto.BookRequestDto;
import com.groupeisi.catalog.dto.BookResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookService {
    List<BookResponseDto> findAll();
    Page<BookResponseDto> findAll(Pageable pageable);
    BookResponseDto findById(Long id);
    BookResponseDto create(BookRequestDto dto);
    BookResponseDto update(Long id, BookRequestDto dto);
    void delete(Long id);
    void decrementStock(Long bookId);
    void incrementStock(Long bookId);
}
