package com.fulfilment.application.monolith.warehouses.adapters.database;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Serializes changes that depend on counts across multiple warehouse rows. */
@Entity
public class WarehouseLock {
  @Id public Long id;
}
