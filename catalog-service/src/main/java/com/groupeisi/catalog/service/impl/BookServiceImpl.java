package com.groupeisi.catalog.service.impl;

import com.groupeisi.catalog.dto.BookRequestDto;
import com.groupeisi.catalog.dto.BookResponseDto;
import com.groupeisi.catalog.entities.BookEntity;
import com.groupeisi.catalog.exception.ConcurrentStockUpdateException;
import com.groupeisi.catalog.exception.EntityNotFoundException;
import com.groupeisi.catalog.exception.RequestException;
import com.groupeisi.catalog.mapping.BookMapper;
import com.groupeisi.catalog.metrics.CatalogBusinessMetrics;
import com.groupeisi.catalog.repository.BookRepository;
import com.groupeisi.catalog.service.BookService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookServiceImpl implements BookService {
    private final BookRepository repository;
    private final BookMapper mapper;
    private final CatalogBusinessMetrics metrics;

    @Override
    public List<BookResponseDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public Page<BookResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    @Cacheable(value = "bookDto", key = "#id")
    public BookResponseDto findById(Long id) {
        BookEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));
        return mapper.toDto(entity);
    }

    @Override
    @Transactional
    @CachePut(value = "bookDto", key = "#result.id")
    public BookResponseDto create(BookRequestDto dto) {
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    @Transactional
    @CachePut(value = "bookDto", key = "#id")
    public BookResponseDto update(Long id, BookRequestDto dto) {
        BookEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + id));
        entity.setTitle(dto.getTitle());
        entity.setAuthor(dto.getAuthor());
        entity.setCategory(dto.getCategory());
        entity.setAvailableStock(dto.getAvailableStock());
        return mapper.toDto(repository.save(entity));
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookDto", key = "#id")
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Book not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookDto", key = "#bookId")
    public void decrementStock(Long bookId) {
        BookEntity entity = repository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + bookId));
        int stock = entity.getAvailableStock() != null ? entity.getAvailableStock() : 0;
        if (stock <= 0) {
            throw new RequestException("No stock available for book " + bookId);
        }
        entity.setAvailableStock(stock - 1);
        saveWithOptimisticLock(entity, bookId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "bookDto", key = "#bookId")
    public void incrementStock(Long bookId) {
        BookEntity entity = repository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found with id: " + bookId));
        int stock = entity.getAvailableStock() != null ? entity.getAvailableStock() : 0;
        entity.setAvailableStock(stock + 1);
        saveWithOptimisticLock(entity, bookId);
    }

    private void saveWithOptimisticLock(BookEntity entity, Long bookId) {
        try {
            repository.save(entity);
            log.debug("Stock updated for bookId={}, version={}", bookId, entity.getVersion());
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            log.warn("Concurrent stock update conflict for bookId={}", bookId, ex);
            metrics.incrementOptimisticLockConflicts();
            throw new ConcurrentStockUpdateException("Concurrent stock update for book " + bookId);
        }
    }
}
