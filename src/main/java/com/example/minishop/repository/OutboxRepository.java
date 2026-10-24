package com.example.minishop.repository;

import com.example.minishop.domain.OutboxRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxRow, Long> {

    @Query("SELECT o FROM OutboxRow o WHERE o.publishedAt IS NULL ORDER BY o.createdAt ASC")
    List<OutboxRow> findUnsent();
}