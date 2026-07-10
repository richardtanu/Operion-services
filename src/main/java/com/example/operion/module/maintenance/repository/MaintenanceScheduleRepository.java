package com.example.operion.module.maintenance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.operion.module.maintenance.entity.MaintenanceSchedule;

@Repository
public interface MaintenanceScheduleRepository extends JpaRepository<MaintenanceSchedule, UUID> {

    List<MaintenanceSchedule> findByTenantId(UUID tenantId);

    List<MaintenanceSchedule> findByAirsoftUnitId(UUID unitId);

    List<MaintenanceSchedule> findByActiveTrueAndNextDueDateLessThanEqual(
            LocalDate date);
}