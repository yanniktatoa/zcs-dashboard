package com.groupeisi.loan.client;

import com.groupeisi.loan.dto.UserResponseDto;
import com.groupeisi.loan.exception.ServiceUnavailableException;
import com.groupeisi.loan.metrics.LoanBusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    private final LoanBusinessMetrics metrics;

    @Override
    public UserClient create(Throwable cause) {
        return userId -> {
            metrics.incrementFeignFailures();
            log.error("User service call failed for userId={}", userId, cause);
            throw new ServiceUnavailableException("User service temporarily unavailable");
        };
    }
}
