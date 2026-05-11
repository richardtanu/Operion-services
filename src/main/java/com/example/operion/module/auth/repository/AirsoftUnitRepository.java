package com.example.operion.module.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.auth.entity.AirsoftUnit;

public interface AirsoftUnitRepository
        extends JpaRepository<AirsoftUnit, UUID> {
}