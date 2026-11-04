package com.groupeisi.catalog.repository;

import com.groupeisi.catalog.entities.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<BookEntity, Long> {
}
