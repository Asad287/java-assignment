package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException.Reason.*;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.models.WarehouseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;

class CreateWarehouseUseCaseTest extends WarehouseTestSupport {

  @Test
  void createsAtExactLocationCapacityAndIgnoresOtherLocations() {
    when(store.getAll()).thenReturn(List.of(existing("OTHER", "TILBURG-001", 40, 1, 2)));
    var warehouse = warehouse("NEW", "ZWOLLE-001", 40, 40);
    new CreateWarehouseUseCase(store, validator).create(warehouse);
    assertNotNull(warehouse.createdAt);
    assertNull(warehouse.archivedAt);
    var order = inOrder(store);
    order.verify(store).lock();
    order.verify(store).findByBusinessUnitCode("NEW");
    order.verify(store).getAll();
    order.verify(store).create(warehouse);
  }

  @Test
  void rejectsDuplicateCodeWithoutPersisting() {
    var warehouse = warehouse("DUPLICATE", "ZWOLLE-001", 20, 0);
    when(store.findByBusinessUnitCode("DUPLICATE")).thenReturn(warehouse);
    var exception = assertThrows(WarehouseException.class,
        () -> new CreateWarehouseUseCase(store, validator).create(warehouse));
    assertEquals(CONFLICT, exception.getReason());
    verify(store, never()).create(any());
  }

  @Test
  void rejectsLocationWarehouseCountLimit() {
    when(store.getAll()).thenReturn(List.of(existing("OLD", "ZWOLLE-001", 10, 0, 1)));
    var exception = assertThrows(WarehouseException.class,
        () -> new CreateWarehouseUseCase(store, validator).create(warehouse("NEW", "ZWOLLE-001", 10, 0)));
    assertTrue(exception.getMessage().contains("number"));
    verify(store, never()).create(any());
  }

  @Test
  void checksCombinedCapacityAndAllowsExactBoundary() {
    when(store.getAll()).thenReturn(List.of(existing("OLD", "ZWOLLE-002", 30, 0, 1)));
    var useCase = new CreateWarehouseUseCase(store, validator);
    assertThrows(WarehouseException.class, () -> useCase.create(warehouse("NEW", "ZWOLLE-002", 21, 0)));
    var warehouse = warehouse("NEW", "ZWOLLE-002", 20, 0);
    useCase.create(warehouse);
    verify(store).create(warehouse);
  }

  @ParameterizedTest
  @MethodSource("invalidWarehouses")
  void rejectsInvalidInput(Warehouse warehouse) {
    var exception = assertThrows(WarehouseException.class,
        () -> new CreateWarehouseUseCase(store, validator).create(warehouse));
    assertEquals(INVALID, exception.getReason());
    verify(store, never()).create(any());
  }

  static Stream<Warehouse> invalidWarehouses() {
    var helper = new CreateWarehouseUseCaseTest();
    var withId = helper.warehouse("NEW", "ZWOLLE-001", 20, 0);
    withId.id = 1L;
    return Stream.of(null, withId,
        helper.warehouse(null, "ZWOLLE-001", 20, 0),
        helper.warehouse(" ", "ZWOLLE-001", 20, 0),
        helper.warehouse("x".repeat(256), "ZWOLLE-001", 20, 0),
        helper.warehouse("NEW", null, 20, 0),
        helper.warehouse("NEW", "UNKNOWN", 20, 0),
        helper.warehouse("NEW", "ZWOLLE-001", null, 0),
        helper.warehouse("NEW", "ZWOLLE-001", 0, 0),
        helper.warehouse("NEW", "ZWOLLE-001", -1, 0),
        helper.warehouse("NEW", "ZWOLLE-001", 20, null),
        helper.warehouse("NEW", "ZWOLLE-001", 20, -1),
        helper.warehouse("NEW", "ZWOLLE-001", 20, 21),
        helper.warehouse("NEW", "ZWOLLE-001", 41, 1),
        helper.warehouse("NEW", "ZWOLLE-001", Integer.MAX_VALUE, 0));
  }
}
