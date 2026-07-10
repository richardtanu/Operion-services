package com.example.operion.module.parthistory.entity;

import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.unitpart.entity.AirsoftUnitPart;
import com.example.operion.module.unitpart.enums.PartCondition;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "part_condition_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartConditionHistory extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "airsoft_unit_part_id")
  private AirsoftUnitPart airsoftUnitPart;

  @Enumerated(EnumType.STRING)
  private PartCondition previousCondition;

  @Enumerated(EnumType.STRING)
  private PartCondition newCondition;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @Column(columnDefinition = "TEXT")
  private String technicianAssessment;
}