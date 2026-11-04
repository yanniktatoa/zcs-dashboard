package com.groupeisi.loan.service.impl;

import com.groupeisi.loan.client.BookClient;
import com.groupeisi.loan.client.UserClient;
import com.groupeisi.loan.dto.BookResponseDto;
import com.groupeisi.loan.dto.LoanRequestDto;
import com.groupeisi.loan.dto.UserResponseDto;
import com.groupeisi.loan.entities.OutboxEventEntity;
import com.groupeisi.loan.kafka.producer.LoanEventProducer;
import com.groupeisi.loan.repository.LoanRepository;
import com.groupeisi.loan.repository.OutboxEventRepository;
import com.groupeisi.loan.service.LoanService;
import com.groupeisi.loan.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Valide la persistance atomique loan + outbox (H2) et l'absence d'appel Kafka direct.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoanServiceTransactionIT {

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private UserClient userClient;

    @MockBean
    private BookClient bookClient;

    @MockBean
    private LoanEventProducer loanEventProducer;

    private LoanRequestDto request;
    private UserResponseDto user;
    private BookResponseDto book;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        outboxEventRepository.deleteAll();

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
    }

    @Test
    void create_shouldPersistLoanAndOutboxEvent() {
        when(userClient.getUserById(1L)).thenReturn(user);
        when(bookClient.getBookById(10L)).thenReturn(book);

        loanService.create(request);

        assertThat(loanRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        OutboxEventEntity event = outboxEventRepository.findAll().get(0);
        assertThat(event.getEventType()).isEqualTo(OutboxPublisherService.LOAN_CREATED);
        assertThat(event.getAggregateType()).isEqualTo("LOAN");
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getPayload()).contains("\"bookId\":10");

        verify(loanEventProducer, never()).loanCreated(any());
    }
}
