package com.groupeisi.loan.service;

import com.groupeisi.loan.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LoanService {
    LoanResponseDto create(LoanRequestDto dto);

    LoanResponseDto returnBook(Long id);

    List<LoanResponseDto> findAll();

    Page<LoanResponseDto> findAll(Pageable pageable);

    List<LoanResponseDto> findByUser(Long userId);

    boolean hasActiveLoans(Long userId);
}
