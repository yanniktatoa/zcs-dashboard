package com.groupeisi.catalog.kafka.consumer;

import com.groupeisi.catalog.config.KafkaConfig;
import com.groupeisi.catalog.dto.LoanEventDto;
import com.groupeisi.catalog.repository.ProcessedEventRepository;
import com.groupeisi.catalog.service.BookService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaConfig.LOAN_CREATED_TOPIC,
                KafkaConfig.LOAN_CREATED_DLT,
                KafkaConfig.LOAN_RETURNED_TOPIC,
                KafkaConfig.LOAN_RETURNED_DLT
        }
)
@ActiveProfiles("kafka-it")
class LoanEventConsumerKafkaIT {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockBean
    private BookService bookService;

    @MockBean
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        when(processedEventRepository.existsByEventId(anyLong())).thenReturn(false);
    }

    @Test
    void onLoanCreated_shouldConsumeMessageAndDecrementStock() throws Exception {
        LoanEventDto event = new LoanEventDto();
        event.setEventId(100L);
        event.setBookId(10L);

        kafkaTemplate.send(KafkaConfig.LOAN_CREATED_TOPIC, event).get();

        verify(bookService, org.mockito.Mockito.timeout(15000).atLeastOnce()).decrementStock(10L);
    }

    @Test
    void onLoanCreated_shouldRetryThenSendToDlt_whenProcessingFails() throws Exception {
        doThrow(new RuntimeException("simulated failure"))
                .when(bookService)
                .decrementStock(anyLong());

        LoanEventDto event = new LoanEventDto();
        event.setEventId(200L);
        event.setBookId(20L);

        kafkaTemplate.send(KafkaConfig.LOAN_CREATED_TOPIC, event).get();

        verify(bookService, org.mockito.Mockito.timeout(20000).atLeast(4)).decrementStock(20L);

        try (Consumer<String, LoanEventDto> dltConsumer = createDltConsumer(KafkaConfig.LOAN_CREATED_DLT)) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, KafkaConfig.LOAN_CREATED_DLT);
            ConsumerRecord<String, LoanEventDto> dltRecord =
                    org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord(
                            dltConsumer, KafkaConfig.LOAN_CREATED_DLT, Duration.ofSeconds(20));
            assertThat(dltRecord.value().getBookId()).isEqualTo(20L);
            assertThat(dltRecord.value().getEventId()).isEqualTo(200L);
        }
    }

    @Test
    void onLoanCreated_shouldSkipDuplicateEvent() throws Exception {
        when(processedEventRepository.existsByEventId(300L)).thenReturn(true);

        LoanEventDto event = new LoanEventDto();
        event.setEventId(300L);
        event.setBookId(30L);

        kafkaTemplate.send(KafkaConfig.LOAN_CREATED_TOPIC, event).get();

        Thread.sleep(8000);
        verify(bookService, never()).decrementStock(30L);
    }

    private Consumer<String, LoanEventDto> createDltConsumer(String topic) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps("dlt-test-group", "true", embeddedKafka));
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        JsonDeserializer<LoanEventDto> deserializer = new JsonDeserializer<>(LoanEventDto.class, false);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        DefaultKafkaConsumerFactory<String, LoanEventDto> factory =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
        Consumer<String, LoanEventDto> consumer = factory.createConsumer();
        consumer.subscribe(java.util.List.of(topic));
        return consumer;
    }
}
