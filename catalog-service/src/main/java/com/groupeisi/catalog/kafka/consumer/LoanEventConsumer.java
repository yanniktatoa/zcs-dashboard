package com.groupeisi.catalog.kafka.consumer;

import com.groupeisi.catalog.dto.LoanEventDto;
import com.groupeisi.catalog.entities.ProcessedEventEntity;
import com.groupeisi.catalog.repository.ProcessedEventRepository;
import com.groupeisi.catalog.metrics.CatalogBusinessMetrics;
import com.groupeisi.catalog.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoanEventConsumer {

    private static final String LOAN_CREATED = "LOAN_CREATED";
    private static final String LOAN_RETURNED = "LOAN_RETURNED";

    private final BookService bookService;
    private final ProcessedEventRepository processedEventRepository;
    private final CatalogBusinessMetrics metrics;

    @KafkaListener(topics = "loan-created", groupId = "catalog-group", containerFactory = "loanEventListenerFactory")
    @Transactional
    public void onLoanCreated(LoanEventDto event) {
        handleEvent(event, LOAN_CREATED, () -> bookService.decrementStock(event.getBookId()));
    }

    @KafkaListener(topics = "loan-returned", groupId = "catalog-group", containerFactory = "loanEventListenerFactory")
    @Transactional
    public void onLoanReturned(LoanEventDto event) {
        handleEvent(event, LOAN_RETURNED, () -> bookService.incrementStock(event.getBookId()));
    }

    private void handleEvent(LoanEventDto event, String eventType, Runnable action) {
        if (event.getEventId() != null && processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Skipping duplicate Kafka event eventId={}", event.getEventId());
            return;
        }
        action.run();
        if (LOAN_CREATED.equals(eventType)) {
            metrics.incrementLoans();
        } else if (LOAN_RETURNED.equals(eventType)) {
            metrics.incrementReturns();
        }
        if (event.getEventId() != null) {
            processedEventRepository.save(ProcessedEventEntity.builder()
                    .eventId(event.getEventId())
                    .eventType(eventType)
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }
}
