package com.example.operion.module.barcodeblock.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.operion.module.barcodeblock.entity.BarcodeCounter;

public interface BarcodeCounterRepository extends JpaRepository<BarcodeCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BarcodeCounter c where c.id = :id")
    Optional<BarcodeCounter> findByIdForUpdate(@Param("id") Long id);
}
