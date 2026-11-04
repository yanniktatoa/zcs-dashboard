package com.groupeisi.catalog.service.impl;

import com.groupeisi.catalog.entities.BookEntity;
import com.groupeisi.catalog.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookServiceOptimisticLockIT {

    @Autowired
    private BookServiceImpl bookService;

    @Autowired
    private BookRepository bookRepository;

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
        bookRepository.save(BookEntity.builder()
                .title("Distributed Systems")
                .author("Author")
                .category("CS")
                .availableStock(1)
                .build());
    }

    @Test
    void decrementStock_shouldAllowOnlyOneSuccessfulUpdateUnderConcurrency() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        Runnable task = () -> {
            ready.countDown();
            try {
                start.await();
                bookService.decrementStock(1L);
            } catch (RuntimeException ex) {
                failures.incrementAndGet();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        executor.submit(task);
        executor.submit(task);
        ready.await();
        start.countDown();
        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(50);
        }

        assertThat(failures.get()).isGreaterThanOrEqualTo(1);
        assertThat(bookRepository.findById(1L).orElseThrow().getAvailableStock()).isZero();
    }
}
