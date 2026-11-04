package com.groupeisi.loan.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupeisi.loan.client.BookClient;
import com.groupeisi.loan.client.UserClient;
import com.groupeisi.loan.dto.BookResponseDto;
import com.groupeisi.loan.dto.LoanEventDto;
import com.groupeisi.loan.dto.LoanRequestDto;
import com.groupeisi.loan.dto.LoanResponseDto;
import com.groupeisi.loan.dto.UserResponseDto;
import com.groupeisi.loan.entities.LoanEntity;
import com.groupeisi.loan.entities.OutboxEventEntity;
import com.groupeisi.loan.exception.EntityNotFoundException;
import com.groupeisi.loan.exception.RequestException;
import com.groupeisi.loan.mapping.LoanMapper;
import com.groupeisi.loan.metrics.LoanBusinessMetrics;
import com.groupeisi.loan.repository.LoanRepository;
import com.groupeisi.loan.repository.OutboxEventRepository;
import com.groupeisi.loan.service.LoanService;
import com.groupeisi.loan.service.OutboxPublisherService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional Outbox : loan + outbox event dans la même transaction SQL.
 * Publication Kafka déléguée à {@link com.groupeisi.loan.service.OutboxPublisherService}.
 */
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {
    private static final String STATUS_ONGOING  = "ONGOING";
    private static final String STATUS_RETURNED = "RETURNED";

    private final LoanRepository repository;
    private final OutboxEventRepository outboxRepository;
    private final LoanMapper mapper;
    private final UserClient userClient;
    private final BookClient bookClient;
    private final ObjectMapper objectMapper;
    private final LoanBusinessMetrics metrics;

    @Transactional
    @Override
    public LoanResponseDto create(LoanRequestDto dto) {
        fetchUser(dto.getUserId());
        BookResponseDto b = fetchBook(dto.getBookId());
        if (b.getAvailableStock() == null || b.getAvailableStock() <= 0) {
            throw new RequestException("Book out of stock");
        }
        LoanEntity saved = repository.save(LoanEntity.builder()
                .userId(dto.getUserId())
                .bookId(dto.getBookId())
                .loanDate(LocalDate.now())
                .status(STATUS_ONGOING)
                .build());

        LoanEventDto eventDto = new LoanEventDto();
        eventDto.setBookId(dto.getBookId());
        enqueueOutboxEvent(OutboxPublisherService.LOAN_CREATED, saved, eventDto);

        metrics.incrementLoansCreated();
        return mapper.toDto(saved);
    }

    @Transactional
    @Override
    public LoanResponseDto returnBook(Long id) {
        LoanEntity e = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Loan not found with id: " + id));
        if (STATUS_RETURNED.equals(e.getStatus())) {
            throw new RequestException("Loan already returned");
        }
        e.setStatus(STATUS_RETURNED);
        e.setReturnDate(LocalDate.now());
        LoanEntity saved = repository.save(e);

        LoanEventDto eventDto = new LoanEventDto();
        eventDto.setBookId(saved.getBookId());
        enqueueOutboxEvent(OutboxPublisherService.LOAN_RETURNED, saved, eventDto);

        return mapper.toDto(saved);
    }

    @Override
    public List<LoanResponseDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public Page<LoanResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDto);
    }

    @Override
    public List<LoanResponseDto> findByUser(Long userId) {
        return repository.findByUserId(userId).stream().map(mapper::toDto).toList();
    }

    @Override
    public boolean hasActiveLoans(Long userId) {
        return repository.existsByUserIdAndStatus(userId, STATUS_ONGOING);
    }

    private void enqueueOutboxEvent(String eventType, LoanEntity loan, LoanEventDto eventDto) {
        try {
            String payload = objectMapper.writeValueAsString(eventDto);
            OutboxEventEntity outbox = OutboxEventEntity.builder()
                    .eventType(eventType)
                    .aggregateType("LOAN")
                    .aggregateId(loan.getId())
                    .payload(payload)
                    .published(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            outboxRepository.save(outbox);
            metrics.incrementOutboxEvents();
        } catch (JsonProcessingException e) {
            throw new RequestException("Failed to serialize outbox event");
        }
    }

    private UserResponseDto fetchUser(Long userId) {
        try {
            return userClient.getUserById(userId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
    }

    private BookResponseDto fetchBook(Long bookId) {
        try {
            return bookClient.getBookById(bookId);
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Book not found with id: " + bookId);
        }
    }
}
