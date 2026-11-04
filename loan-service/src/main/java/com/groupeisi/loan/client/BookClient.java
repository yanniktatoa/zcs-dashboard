package com.groupeisi.loan.client;

import com.groupeisi.loan.dto.BookResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "catalogService",
        url = "${clients.catalog.url}",
        fallbackFactory = BookClientFallbackFactory.class
)
public interface BookClient {
    @GetMapping("/books/{id}")
    BookResponseDto getBookById(@PathVariable("id") Long id);
}
