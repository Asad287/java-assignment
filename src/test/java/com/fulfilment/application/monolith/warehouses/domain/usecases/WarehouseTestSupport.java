package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.location.LocationGateway;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import java.util.List;

abstract class WarehouseTestSupport {
  WarehouseStore store;
  WarehouseValidator validator;

  @BeforeEach
  void setUp() {
    store = mock(WarehouseStore.class);
    when(store.getAll()).thenReturn(List.of());
    validator = new WarehouseValidator(new LocationGateway(), store);
  }

  Warehouse warehouse(String code, String location, Integer capacity, Integer stock) {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = code;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    return warehouse;
  }

  Warehouse existing(String code, String location, int capacity, int stock, long id) {
    var warehouse = warehouse(code, location, capacity, stock);
    warehouse.id = id;
    warehouse.createdAt = java.time.LocalDateTime.of(2024, 1, 1, 0, 0);
    return warehouse;
  }
}
