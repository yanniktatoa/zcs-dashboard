package com.groupeisi.loan.repository;

import com.groupeisi.loan.entities.LoanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<LoanEntity, Long> {
    List<LoanEntity> findByUserId(Long userId);

    boolean existsByUserIdAndStatus(Long userId, String status);
}
