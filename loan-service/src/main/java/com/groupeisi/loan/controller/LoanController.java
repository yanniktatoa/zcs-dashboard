package com.groupeisi.loan.controller;

import com.groupeisi.loan.dto.*;
import com.groupeisi.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@RegisterReflectionForBinding({
    LoanRequestDto.class,
    LoanResponseDto.class,
    BookResponseDto.class,
    UserResponseDto.class,
    LoanEventDto.class
})
@RestController
@RequestMapping("/loans")
@RequiredArgsConstructor
public class LoanController {
    private final LoanService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponseDto create(@Valid @RequestBody LoanRequestDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}/return")
    public LoanResponseDto returnBook(@PathVariable Long id) {
        return service.returnBook(id);
    }

    @GetMapping
    public List<LoanResponseDto> getAll() {
        return service.findAll();
    }

    @GetMapping("/paginated")
    public Page<LoanResponseDto> getPaginated(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/user/{userId}")
    public List<LoanResponseDto> byUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }
}
