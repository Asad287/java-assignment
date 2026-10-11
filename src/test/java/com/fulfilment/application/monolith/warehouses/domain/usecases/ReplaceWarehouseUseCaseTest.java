package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import org.junit.jupiter.api.Test;
import java.util.List;

class ReplaceWarehouseUseCaseTest extends WarehouseTestSupport {

  @Test
  void archivesOldRowBeforeCreatingReplacementAtFullLocation() {
    var old = existing("CODE", "ZWOLLE-001", 40, 10, 1);
    when(store.findByBusinessUnitCode("CODE")).thenReturn(old);
    when(store.getAll()).thenReturn(List.of(old));
    var replacement = warehouse("CODE", "ZWOLLE-001", 30, 10);
    new ReplaceWarehouseUseCase(store, validator).replace(replacement);
    assertEquals(java.time.LocalDateTime.of(2024, 1, 1, 0, 0), old.createdAt);
    assertEquals(old.archivedAt, replacement.createdAt);
    assertNull(replacement.archivedAt);
    var order = inOrder(store);
    order.verify(store).lock();
    order.verify(store).findByBusinessUnitCode("CODE");
    order.verify(store).getAll();
    order.verify(store).update(old);
    order.verify(store).create(replacement);
  }

  @Test
  void rejectsMissingActiveWarehouse() {
    var exception = assertThrows(WarehouseException.class,
        () -> new ReplaceWarehouseUseCase(store, validator).replace(warehouse("MISSING", "ZWOLLE-001", 20, 0)));
    assertEquals(WarehouseException.Reason.NOT_FOUND, exception.getReason());
    verify(store, never()).update(any());
  }

  @Test
  void rejectsNullAndBlankCodes() {
    var useCase = new ReplaceWarehouseUseCase(store, validator);
    assertThrows(WarehouseException.class, () -> useCase.replace(null));
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse(null, "ZWOLLE-001", 20, 0)));
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse(" ", "ZWOLLE-001", 20, 0)));
  }

  @Test
  void rejectsInsufficientCapacityAndMismatchedStockBeforeArchiving() {
    var old = existing("CODE", "ZWOLLE-001", 40, 20, 1);
    when(store.findByBusinessUnitCode("CODE")).thenReturn(old);
    var useCase = new ReplaceWarehouseUseCase(store, validator);
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse("CODE", "ZWOLLE-001", 10, 10)));
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse("CODE", "ZWOLLE-001", 40, 19)));
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse("CODE", "ZWOLLE-001", 40, 21)));
    assertNull(old.archivedAt);
    verify(store, never()).update(any());
    verify(store, never()).create(any());
  }

  @Test
  void checksDestinationCapacityWhenMovingToAnotherLocation() {
    var old = existing("CODE", "ZWOLLE-001", 40, 10, 1);
    var other = existing("OTHER", "ZWOLLE-002", 40, 5, 2);
    when(store.findByBusinessUnitCode("CODE")).thenReturn(old);
    when(store.getAll()).thenReturn(List.of(old, other));
    var useCase = new ReplaceWarehouseUseCase(store, validator);
    assertThrows(WarehouseException.class, () -> useCase.replace(warehouse("CODE", "ZWOLLE-002", 11, 10)));
    var replacement = warehouse("CODE", "ZWOLLE-002", 10, 10);
    useCase.replace(replacement);
    verify(store).create(replacement);
  }
}
