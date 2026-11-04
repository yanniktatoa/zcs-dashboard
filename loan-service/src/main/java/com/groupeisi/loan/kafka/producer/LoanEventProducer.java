package com.groupeisi.loan.kafka.producer;

import com.groupeisi.loan.dto.LoanEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanEventProducer {
    private final KafkaTemplate<String, LoanEventDto> template;

    public void loanCreated(LoanEventDto e) {
        template.send("loan-created", e);
    }

    public void loanReturned(LoanEventDto e) {
        template.send("loan-returned", e);
    }
}
