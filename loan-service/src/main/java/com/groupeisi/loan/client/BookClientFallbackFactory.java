package com.groupeisi.loan.client;

import com.groupeisi.loan.dto.BookResponseDto;
import com.groupeisi.loan.exception.ServiceUnavailableException;
import com.groupeisi.loan.metrics.LoanBusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookClientFallbackFactory implements FallbackFactory<BookClient> {

    private final LoanBusinessMetrics metrics;

    @Override
    public BookClient create(Throwable cause) {
        return bookId -> {
            metrics.incrementFeignFailures();
            log.error("Catalog service call failed for bookId={}", bookId, cause);
            throw new ServiceUnavailableException("Catalog service temporarily unavailable");
        };
    }
}
