package com.example.operion.module.part.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartTest {

  @Test
  void shouldDefaultCurrentStockToZeroBeforePersist() {
    Part part = new Part();

    part.prePersist();

    assertEquals(0, part.getCurrentStock());
  }

  /**
   * BE-04 (OPERION_BE_CHANGE_QUEUE.md): isConsumable() must key off the
   * category's stored flag, not its display name — a tenant renaming or
   * localizing "Consumable" (e.g. to "Konsumabel") must not silently stop
   * every dependent behaviour (scan-in, take/exhaust, goods-receipt instance
   * generation, burn rate eligibility).
   */
  @Test
  void isConsumableFollowsTheStoredFlagNotTheDisplayName() {
    PartCategory renamedButStillConsumable = PartCategory.builder()
        .name("Konsumabel")
        .consumable(true)
        .build();

    Part part = Part.builder().category(renamedButStillConsumable).build();

    assertTrue(part.isConsumable());
  }

  @Test
  void isConsumableIsFalseForACategoryNamedConsumableWithTheFlagUnset() {
    PartCategory nameOnlyMatch = PartCategory.builder()
        .name("Consumable")
        .build();

    Part part = Part.builder().category(nameOnlyMatch).build();

    assertFalse(part.isConsumable());
  }

  @Test
  void isConsumableIsFalseWithNoCategory() {
    Part part = Part.builder().build();

    assertFalse(part.isConsumable());
  }
}
