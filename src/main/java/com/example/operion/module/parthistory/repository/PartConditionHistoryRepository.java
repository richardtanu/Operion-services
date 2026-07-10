package com.example.operion.module.parthistory.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.part.entity.Part;
import com.example.operion.module.parthistory.entity.PartConditionHistory;

public interface PartConditionHistoryRepository
                extends JpaRepository<PartConditionHistory, UUID> {

        List<PartConditionHistory> findByAirsoftUnitPart_IdOrderByCreatedAtDesc(
                        UUID unitPartId);
}