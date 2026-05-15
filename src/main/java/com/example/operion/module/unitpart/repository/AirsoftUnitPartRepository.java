package com.example.operion.module.unitpart.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.UnitPartStatus;

public interface AirsoftUnitPartRepository
    extends JpaRepository<AirsoftUnitPart, UUID> {

  List<AirsoftUnitPart> findByAirsoftUnitIdAndStatus(
      UUID airsoftUnitId,
      UnitPartStatus status);
}