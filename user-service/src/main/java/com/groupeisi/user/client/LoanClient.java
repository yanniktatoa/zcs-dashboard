package com.groupeisi.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "loanService", url = "${clients.loan.url}")
public interface LoanClient {

    @GetMapping("/loans/user/{userId}/active/exists")
    boolean hasActiveLoans(@PathVariable("userId") Long userId);
}
