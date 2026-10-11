package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fulfilment.application.monolith.TestDataSupport;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@QuarkusTest
class WarehouseEndpointTest extends TestDataSupport {

  @Inject ReplaceWarehouseOperation replacement;

  @Test
  void createsRetrievesListsAndArchivesWithoutDeletingHistory() {
    String id = createWarehouse("NEW", "ZWOLLE-001", 40, 10);
    given().get("/warehouse/" + id).then().statusCode(200).body("id", equalTo(id), "stock", equalTo(10));
    given().get("/warehouse").then().statusCode(200).body("size()", equalTo(1));
    given().delete("/warehouse/" + id).then().statusCode(204);
    given().get("/warehouse/" + id).then().statusCode(404);
    given().delete("/warehouse/" + id).then().statusCode(404);
    given().get("/warehouse").then().statusCode(200).body("size()", equalTo(0));
    assertNotNull(warehouses.findById(Long.valueOf(id)).archivedAt);
    assertEquals(10, warehouses.findById(Long.valueOf(id)).stock);
    createWarehouse("NEW", "ZWOLLE-001", 40, 0);
    assertEquals(2, warehouses.count());
  }

  @Test
  void replacesAtAFullLocationAndKeepsOldIdentityAndHistory() {
    String oldId = createWarehouse("REPLACE", "ZWOLLE-001", 40, 10);
    String newId = given().contentType("application/json")
        .body(Map.of("businessUnitCode", "REPLACE", "location", "ZWOLLE-001", "capacity", 30, "stock", 10))
        .post("/warehouse/REPLACE/replacement").then().statusCode(200)
        .body("stock", equalTo(10)).extract().path("id");
    assertNotEquals(oldId, newId);
    assertNotNull(warehouses.findById(Long.valueOf(oldId)).archivedAt);
    assertNull(warehouses.findById(Long.valueOf(newId)).archivedAt);
    given().get("/warehouse/" + oldId).then().statusCode(404);
    given().get("/warehouse").then().statusCode(200).body("size()", equalTo(1));
  }

  @Test
  void invalidReplacementDoesNotArchiveTheOriginal() {
    String id = createWarehouse("CODE", "ZWOLLE-001", 40, 20);
    given().contentType("application/json")
        .body(Map.of("location", "ZWOLLE-001", "capacity", 30, "stock", 19))
        .post("/warehouse/CODE/replacement").then().statusCode(400);
    given().contentType("application/json")
        .body(Map.of("businessUnitCode", "OTHER", "location", "ZWOLLE-001", "capacity", 30, "stock", 20))
        .post("/warehouse/CODE/replacement").then().statusCode(400);
    given().get("/warehouse/" + id).then().statusCode(200).body("stock", equalTo(20));
    assertEquals(1, warehouses.count());
  }

  @Test
  void rollbackRestoresOriginalAndRemovesReplacement() {
    String id = createWarehouse("CODE", "ZWOLLE-001", 40, 10);
    assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
      var warehouse = new Warehouse();
      warehouse.businessUnitCode = "CODE";
      warehouse.location = "ZWOLLE-001";
      warehouse.capacity = 30;
      warehouse.stock = 10;
      replacement.replace(warehouse);
      throw new IllegalStateException("Simulated failure before commit");
    }));
    assertEquals(1, warehouses.count());
    assertNull(warehouses.findById(Long.valueOf(id)).archivedAt);
  }

  @Test
  void returnsClearErrorsForInvalidRequests() {
    for (String id : new String[] {"abc", "0", "-1", "99999999999999999999999"}) {
      given().get("/warehouse/" + id).then().statusCode(400).body("code", equalTo(400));
    }
    given().get("/warehouse/999999").then().statusCode(404);
    given().contentType("application/json").body("{}").post("/warehouse").then().statusCode(400);
    given().contentType("application/json").body("null").post("/warehouse").then().statusCode(400);
    given().contentType("application/json").body(Map.of("id", "1")).post("/warehouse").then().statusCode(400);
    given().contentType("application/json").body("{}").post("/warehouse/MISSING/replacement").then().statusCode(404);
  }

  @Test
  void rejectsDuplicatesAndAggregateCapacityOverflow() {
    createWarehouse("CODE", "ZWOLLE-002", 30, 1);
    given().contentType("application/json")
        .body(Map.of("businessUnitCode", "CODE", "location", "AMSTERDAM-001", "capacity", 20, "stock", 0))
        .post("/warehouse").then().statusCode(409);
    given().contentType("application/json")
        .body(Map.of("businessUnitCode", "CODE", "location", "ZWOLLE-002", "capacity", 30, "stock", 1))
        .post("/warehouse").then().statusCode(409);
    given().contentType("application/json")
        .body(Map.of("businessUnitCode", "OTHER", "location", "ZWOLLE-002", "capacity", 21, "stock", 0))
        .post("/warehouse").then().statusCode(400);
    assertEquals(1, warehouses.count());
  }

  @Test
  void serializesConcurrentCreationAtASingleWarehouseLocation() {
    var first = CompletableFuture.supplyAsync(() -> createStatus("ONE"));
    var second = CompletableFuture.supplyAsync(() -> createStatus("TWO"));
    var statuses = java.util.List.of(first.join(), second.join());
    assertTrue(statuses.contains(201));
    assertTrue(statuses.contains(400));
    assertEquals(1, warehouses.count());
  }

  private int createStatus(String code) {
    return given().contentType("application/json")
        .body(Map.of("businessUnitCode", code, "location", "ZWOLLE-001", "capacity", 20, "stock", 0))
        .post("/warehouse").statusCode();
  }
}
