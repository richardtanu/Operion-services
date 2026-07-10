package com.example.operion.module.part.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PartTest {

  @Test
  void shouldDefaultCurrentStockToZeroBeforePersist() {
    Part part = new Part();

    part.prePersist();

    assertEquals(0, part.getCurrentStock());
  }
}
