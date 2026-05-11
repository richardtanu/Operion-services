package com.example.operion.module.auth.entity;

import java.time.LocalDate;
import java.util.UUID;

import com.example.operion.module.auth.enums.UnitStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "airsoft_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirsoftUnit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String name;

    private String brand;

    private String model;

    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnitStatus status;

    private LocalDate purchaseDate;

    @Column(columnDefinition = "TEXT")
    private String notes;
}