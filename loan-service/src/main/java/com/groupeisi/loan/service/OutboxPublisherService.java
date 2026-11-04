package com.groupeisi.loan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupeisi.loan.dto.LoanEventDto;
import com.groupeisi.loan.entities.OutboxEventEntity;
import com.groupeisi.loan.kafka.producer.LoanEventProducer;
import com.groupeisi.loan.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    public static final String LOAN_CREATED = "LOAN_CREATED";
    public static final String LOAN_RETURNED = "LOAN_RETURNED";

    private final OutboxEventRepository outboxRepository;
    private final LoanEventProducer loanEventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> events = outboxRepository.findByPublishedFalse();
        for (OutboxEventEntity event : events) {
            try {
                log.info("Publishing outbox event {}", event.getId());
                LoanEventDto payload = objectMapper.readValue(event.getPayload(), LoanEventDto.class);
                payload.setEventId(event.getId());

                if (LOAN_CREATED.equals(event.getEventType())) {
                    loanEventProducer.loanCreated(payload);
                } else if (LOAN_RETURNED.equals(event.getEventType())) {
                    loanEventProducer.loanReturned(payload);
                } else {
                    log.error("Unknown outbox event type {} for event {}", event.getEventType(), event.getId());
                    continue;
                }

                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Kafka publish failed for outbox event {}", event.getId(), e);
            }
        }
    }
}
