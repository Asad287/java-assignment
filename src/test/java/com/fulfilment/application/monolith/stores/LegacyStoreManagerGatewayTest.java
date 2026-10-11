package com.fulfilment.application.monolith.stores;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LegacyStoreManagerGatewayTest {
  @Test
  void simulationHandlesShortAndUnicodeNames() {
    var gateway = new LegacyStoreManagerGateway();
    var store = new Store("A");
    store.id = 1L;
    assertDoesNotThrow(() -> gateway.createStoreOnLegacySystem(store));
    store.name = "BEST?";
    assertDoesNotThrow(() -> gateway.updateStoreOnLegacySystem(store));
  }
}
