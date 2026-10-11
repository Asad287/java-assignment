package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import org.junit.jupiter.api.Test;

class ArchiveWarehouseUseCaseTest extends WarehouseTestSupport {

  @Test
  void preservesWarehouseDataWhileArchiving() {
    var warehouse = existing("CODE", "ZWOLLE-001", 40, 10, 1);
    when(store.findActiveById(1L)).thenReturn(warehouse);
    new ArchiveWarehouseUseCase(store).archive(warehouse);
    assertNotNull(warehouse.archivedAt);
    assertEquals("CODE", warehouse.businessUnitCode);
    assertEquals(10, warehouse.stock);
    assertEquals(java.time.LocalDateTime.of(2024, 1, 1, 0, 0), warehouse.createdAt);
    verify(store).update(warehouse);
  }

  @Test
  void rejectsUnknownAndNullWarehouses() {
    var useCase = new ArchiveWarehouseUseCase(store);
    assertThrows(WarehouseException.class, () -> useCase.archive(null));
    assertThrows(WarehouseException.class, () -> useCase.archive(existing("MISSING", "ZWOLLE-001", 20, 0, 99)));
    verify(store, never()).update(any());
  }
}
