package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.RealisasiStatusHistory;

public interface RealisasiStatusHistoryRepository extends JpaRepository<RealisasiStatusHistory, UUID> {

    List<RealisasiStatusHistory> findByRealisasi_IdOrderByCreatedAtDesc(UUID realisasiId);
}
