package com.example.minishop.repository;

import com.example.minishop.domain.IdemRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdemRepository extends JpaRepository<IdemRow, Long> {

    Optional<IdemRow> findByIdemKey(String idemKey);
}