package com.fulfilment.application.monolith.location;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocationGatewayTest {
  private final LocationGateway gateway = new LocationGateway();

  @ParameterizedTest
  @ValueSource(strings = {"ZWOLLE-001", "ZWOLLE-002", "AMSTERDAM-001", "AMSTERDAM-002",
      "TILBURG-001", "HELMOND-001", "EINDHOVEN-001", "VETSBY-001"})
  void resolvesKnownLocations(String identifier) {
    var location = gateway.resolveByIdentifier(identifier);
    assertEquals(identifier, location.identification);
    assertTrue(location.maxCapacity > 0);
    assertTrue(location.maxNumberOfWarehouses > 0);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"UNKNOWN", "zwolle-001", " ZWOLLE-001 "})
  void returnsNullForUnknownIdentifiers(String identifier) {
    assertNull(gateway.resolveByIdentifier(identifier));
  }

  @Test
  void callersCannotChangeTheLocationCatalog() {
    gateway.resolveByIdentifier("ZWOLLE-001").maxCapacity = 999;
    assertEquals(40, gateway.resolveByIdentifier("ZWOLLE-001").maxCapacity);
  }
}
