package com.groupeisi.catalog.service.impl;

import com.groupeisi.catalog.entities.BookEntity;
import com.groupeisi.catalog.exception.ConcurrentStockUpdateException;
import com.groupeisi.catalog.exception.EntityNotFoundException;
import com.groupeisi.catalog.mapping.BookMapper;
import com.groupeisi.catalog.metrics.CatalogBusinessMetrics;
import com.groupeisi.catalog.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceConcurrencyTest {

    @Mock
    private BookRepository repository;

    @Mock
    private BookMapper mapper;

    @Mock
    private CatalogBusinessMetrics metrics;

    @InjectMocks
    private BookServiceImpl bookService;

    private BookEntity book;

    @BeforeEach
    void setUp() {
        book = BookEntity.builder()
                .id(1L)
                .title("Clean Code")
                .availableStock(2)
                .version(0L)
                .build();
    }

    @Test
    void decrementStock_shouldUpdateStock_whenNoConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book));
        when(repository.save(any(BookEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> bookService.decrementStock(1L)).doesNotThrowAnyException();

        verify(repository).save(any(BookEntity.class));
        assertThat(book.getAvailableStock()).isEqualTo(1);
    }

    @Test
    void incrementStock_shouldUpdateStock_whenNoConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book));
        when(repository.save(any(BookEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookService.incrementStock(1L);

        assertThat(book.getAvailableStock()).isEqualTo(3);
    }

    @Test
    void decrementStock_shouldThrowConcurrentStockUpdateException_onOptimisticLockConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book));
        when(repository.save(any(BookEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("version conflict"));

        assertThatThrownBy(() -> bookService.decrementStock(1L))
                .isInstanceOf(ConcurrentStockUpdateException.class)
                .hasMessageContaining("Concurrent stock update for book 1");
    }

    @Test
    void incrementStock_shouldThrowConcurrentStockUpdateException_onOptimisticLockConflict() {
        when(repository.findById(1L)).thenReturn(Optional.of(book));
        when(repository.save(any(BookEntity.class)))
                .thenThrow(new OptimisticLockingFailureException("version conflict"));

        assertThatThrownBy(() -> bookService.incrementStock(1L))
                .isInstanceOf(ConcurrentStockUpdateException.class);
    }

    @Test
    void decrementStock_shouldThrowEntityNotFoundException_whenBookMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.decrementStock(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
