package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException.Reason.INVALID;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;

@ApplicationScoped
public class WarehouseValidator {

  private final LocationResolver locationResolver;
  private final WarehouseStore warehouseStore;

  public WarehouseValidator(LocationResolver locationResolver, WarehouseStore warehouseStore) {
    this.locationResolver = locationResolver;
    this.warehouseStore = warehouseStore;
  }

  public void validate(Warehouse warehouse, Long replacedId) {
    var location = validateFields(warehouse);
    validateAvailability(warehouse, replacedId, location);
  }

  Location validateFields(Warehouse warehouse) {
    if (warehouse == null || warehouse.businessUnitCode == null
        || warehouse.businessUnitCode.isBlank() || warehouse.businessUnitCode.length() > 255) {
      throw new WarehouseException(INVALID, "Business unit code is required (maximum 255 characters)");
    }
    if (warehouse.id != null) {
      throw new WarehouseException(INVALID, "Id must not be set on a new warehouse");
    }
    var location = locationResolver.resolveByIdentifier(warehouse.location);
    if (location == null) {
      throw new WarehouseException(INVALID, "Location does not exist");
    }
    if (warehouse.capacity == null || warehouse.capacity <= 0
        || warehouse.stock == null || warehouse.stock < 0 || warehouse.stock > warehouse.capacity) {
      throw new WarehouseException(INVALID, "Capacity must be positive and stock must be between zero and capacity");
    }
    return location;
  }

  void validateAvailability(Warehouse warehouse, Long replacedId, Location location) {
    var existing = warehouseStore.getAll().stream()
        .filter(w -> Objects.equals(w.location, warehouse.location))
        .filter(w -> replacedId == null || !replacedId.equals(w.id))
        .toList();
    if (existing.size() >= location.maxNumberOfWarehouses) {
      throw new WarehouseException(INVALID, "Maximum number of warehouses reached for this location");
    }
    long capacity = existing.stream().mapToLong(w -> w.capacity).sum() + warehouse.capacity;
    if (capacity > location.maxCapacity) {
      throw new WarehouseException(INVALID, "Combined warehouse capacity exceeds the location limit");
    }
  }
}
