package com.example.minishop.repository;

import com.example.minishop.domain.DlqMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {
}