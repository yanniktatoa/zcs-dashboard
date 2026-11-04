package com.groupeisi.loan.repository;

import com.groupeisi.loan.entities.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedFalse();
}
