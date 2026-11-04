package com.groupeisi.loan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupeisi.loan.client.BookClient;
import com.groupeisi.loan.client.UserClient;
import com.groupeisi.loan.dto.BookResponseDto;
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
import com.groupeisi.loan.service.OutboxPublisherService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceImplTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private UserClient userClient;

    @Mock
    private BookClient bookClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LoanBusinessMetrics metrics;

    @InjectMocks
    private LoanServiceImpl loanService;

    private LoanRequestDto request;
    private UserResponseDto user;
    private BookResponseDto book;
    private LoanEntity savedLoan;
    private LoanResponseDto loanResponse;

    @BeforeEach
    void setUp() {
        request = new LoanRequestDto();
        request.setUserId(1L);
        request.setBookId(10L);

        user = new UserResponseDto();
        user.setId(1L);
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setRole("MEMBER");

        book = new BookResponseDto();
        book.setId(10L);
        book.setTitle("Clean Code");
        book.setAvailableStock(3);

        savedLoan = LoanEntity.builder()
                .id(100L)
                .userId(1L)
                .bookId(10L)
                .status("ONGOING")
                .build();

        loanResponse = new LoanResponseDto();
        loanResponse.setId(100L);
        loanResponse.setUserId(1L);
        loanResponse.setBookId(10L);
        loanResponse.setStatus("ONGOING");
    }

    @Test
    void create_shouldSaveLoanAndEnqueueOutboxEvent_whenUserBookExistAndStockAvailable() {
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenReturn(book);
        when(loanRepository.save(any(LoanEntity.class))).thenReturn(savedLoan);
        when(loanMapper.toDto(savedLoan)).thenReturn(loanResponse);

        LoanResponseDto result = loanService.create(request);

        assertThat(result).isEqualTo(loanResponse);
        verify(loanRepository).save(any(LoanEntity.class));
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outbox = outboxCaptor.getValue();
        assertThat(outbox.getEventType()).isEqualTo(OutboxPublisherService.LOAN_CREATED);
        assertThat(outbox.getAggregateType()).isEqualTo("LOAN");
        assertThat(outbox.getAggregateId()).isEqualTo(100L);
        assertThat(outbox.isPublished()).isFalse();
        assertThat(outbox.getPayload()).contains("\"bookId\":10");
    }

    @Test
    void create_shouldThrowRequestException_whenStockIsZero() {
        book.setAvailableStock(0);
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenReturn(book);

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(RequestException.class)
                .hasMessage("Book out of stock");

        verify(loanRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowRequestException_whenStockIsNull() {
        book.setAvailableStock(null);
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenReturn(book);

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(RequestException.class)
                .hasMessage("Book out of stock");

        verify(loanRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowEntityNotFoundException_whenUserFeignReturns404() {
        when(userClient.getUserById(1L)).thenThrow(feignNotFound("/users/1"));

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found with id: 1");

        verify(bookClient, never()).getBookById(any());
        verify(loanRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowEntityNotFoundException_whenBookFeignReturns404() {
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenThrow(feignNotFound("/books/10"));

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Book not found with id: 10");

        verify(loanRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void create_shouldRollbackAndPropagate_whenOutboxSaveFails() {
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenReturn(book);
        when(loanRepository.save(any(LoanEntity.class))).thenReturn(savedLoan);
        doThrow(new RuntimeException("Outbox failure"))
                .when(outboxEventRepository)
                .save(any(OutboxEventEntity.class));

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Outbox failure");

        verify(loanRepository).save(any(LoanEntity.class));
        verify(outboxEventRepository).save(any(OutboxEventEntity.class));
        verify(loanMapper, never()).toDto(any());
    }

    @Test
    void create_shouldThrowEntityNotFoundException_whenUserIsNull() {
        when(userClient.getUserById(1L)).thenReturn(null);

        assertThatThrownBy(() -> loanService.create(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found with id: 1");

        verify(loanRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private static FeignException.NotFound feignNotFound(String path) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                path,
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return new FeignException.NotFound("Not Found", request, null, null);
    }
}
