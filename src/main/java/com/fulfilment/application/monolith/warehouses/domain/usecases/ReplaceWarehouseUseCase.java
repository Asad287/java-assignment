package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException.Reason.INVALID;
import static com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException.Reason.NOT_FOUND;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseValidator validator;

  public ReplaceWarehouseUseCase(WarehouseStore warehouseStore, WarehouseValidator validator) {
    this.warehouseStore = warehouseStore;
    this.validator = validator;
  }

  @Override
  @Transactional
  public void replace(Warehouse newWarehouse) {
    warehouseStore.lock();
    if (newWarehouse == null || newWarehouse.businessUnitCode == null
        || newWarehouse.businessUnitCode.isBlank()) {
      throw new WarehouseException(INVALID, "Business unit code is required");
    }
    var current = warehouseStore.findByBusinessUnitCode(newWarehouse.businessUnitCode);
    if (current == null) {
      throw new WarehouseException(NOT_FOUND, "Active warehouse does not exist");
    }
    validator.validate(newWarehouse, current.id);
    if (newWarehouse.capacity < current.stock) {
      throw new WarehouseException(INVALID, "Replacement capacity cannot accommodate existing stock");
    }
    if (!newWarehouse.stock.equals(current.stock)) {
      throw new WarehouseException(INVALID, "Replacement stock must match existing stock");
    }
    var now = LocalDateTime.now();
    current.archivedAt = now;
    warehouseStore.update(current);
    newWarehouse.createdAt = now;
    newWarehouse.archivedAt = null;
    warehouseStore.create(newWarehouse);
  }
}
