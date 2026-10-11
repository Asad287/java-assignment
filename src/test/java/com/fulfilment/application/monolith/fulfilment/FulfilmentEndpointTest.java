package com.fulfilment.application.monolith.fulfilment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fulfilment.application.monolith.TestDataSupport;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@QuarkusTest
class FulfilmentEndpointTest extends TestDataSupport {

  @Test
  void createsListsAndDeletesAssignmentAndTreatsDuplicateAsIdempotent() {
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    long warehouse = Long.parseLong(createWarehouse("WH", "AMSTERDAM-001", 10, 0));
    long id = assign(store, product, warehouse, 201);
    assertEquals(id, assign(store, product, warehouse, 200));
    given().get("/fulfilment").then().statusCode(200).body("size()", equalTo(1));
    given().delete("/fulfilment/" + id).then().statusCode(204);
    given().delete("/fulfilment/" + id).then().statusCode(404);
    given().get("/fulfilment").then().statusCode(200).body("size()", equalTo(0));
  }

  @Test
  void limitsProductToTwoWarehousesPerStore() {
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    long[] warehouse = warehouses(3);
    assign(store, product, warehouse[0], 201);
    assign(store, product, warehouse[1], 201);
    assign(store, product, warehouse[2], 409);
    long otherStore = createStore("OTHER");
    assign(otherStore, product, warehouse[2], 201);
    assertEquals(3, FulfilmentAssignment.count());
  }

  @Test
  void concurrentAssignmentsCannotExceedProductWarehouseLimit() {
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    long[] warehouse = warehouses(3);
    assign(store, product, warehouse[0], 201);
    var first = CompletableFuture.supplyAsync(() -> assignmentStatus(store, product, warehouse[1]));
    var second = CompletableFuture.supplyAsync(() -> assignmentStatus(store, product, warehouse[2]));
    var statuses = java.util.List.of(first.join(), second.join());
    assertTrue(statuses.contains(201));
    assertTrue(statuses.contains(409));
    assertEquals(2, FulfilmentAssignment.count());
  }

  private int assignmentStatus(long store, long product, long warehouse) {
    return given().contentType("application/json")
        .body(Map.of("storeId", store, "productId", product, "warehouseId", warehouse))
        .post("/fulfilment").statusCode();
  }

  @Test
  void limitsStoreToThreeDistinctWarehousesAcrossProducts() {
    long store = createStore("STORE");
    long[] warehouse = warehouses(4);
    for (int i = 0; i < 3; i++) {
      assign(store, createProduct("P" + i), warehouse[i], 201);
    }
    long product = createProduct("P3");
    assign(store, product, warehouse[3], 409);
    assign(store, product, warehouse[0], 201);
    assertEquals(4, FulfilmentAssignment.count());
  }

  @Test
  void limitsWarehouseToFiveDistinctProductsAcrossStores() {
    long warehouse = warehouses(1)[0];
    long store = createStore("STORE");
    long firstProduct = 0;
    for (int i = 0; i < 5; i++) {
      long product = createProduct("P" + i);
      if (i == 0) firstProduct = product;
      assign(store, product, warehouse, 201);
    }
    assign(store, createProduct("P5"), warehouse, 409);
    assign(createStore("OTHER"), firstProduct, warehouse, 201);
    assertEquals(6, FulfilmentAssignment.count());
  }

  @Test
  void archivingAndReplacementRetainHistoricalAssignmentsButReleaseActiveLimits() {
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    long old = warehouses(1)[0];
    assign(store, product, old, 201);
    String newId = given().contentType("application/json")
        .body(Map.of("location", "AMSTERDAM-001", "capacity", 10, "stock", 0))
        .post("/warehouse/WH0/replacement").then().statusCode(200).extract().path("id");
    given().get("/fulfilment").then().statusCode(200).body("size()", equalTo(0));
    assign(store, product, old, 404);
    assign(store, product, Long.parseLong(newId), 201);
    assertEquals(2, FulfilmentAssignment.count());
    given().delete("/warehouse/" + newId).then().statusCode(204);
    given().get("/fulfilment").then().statusCode(200).body("size()", equalTo(0));
  }

  @Test
  void deletingProductOrStoreRemovesTheirAssignments() {
    long warehouse = warehouses(1)[0];
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    assign(store, product, warehouse, 201);
    given().delete("/product/" + product).then().statusCode(204);
    assertEquals(0, FulfilmentAssignment.count());
    assign(store, createProduct("OTHER"), warehouse, 201);
    given().delete("/store/" + store).then().statusCode(204);
    assertEquals(0, FulfilmentAssignment.count());
  }

  @Test
  void rejectsMissingInvalidAndUnknownIdentifiers() {
    for (String body : new String[] {"null", "{}", "{\"storeId\":1}",
        "{\"storeId\":1,\"productId\":1}", "{\"storeId\":0,\"productId\":1,\"warehouseId\":1}",
        "{\"storeId\":1,\"productId\":0,\"warehouseId\":1}",
        "{\"storeId\":1,\"productId\":1,\"warehouseId\":0}"}) {
      given().contentType("application/json").body(body).post("/fulfilment").then().statusCode(400);
    }
    long store = createStore("STORE");
    long product = createProduct("PRODUCT");
    long warehouse = warehouses(1)[0];
    assign(99999, product, warehouse, 404);
    assign(store, 99999, warehouse, 404);
    assign(store, product, 99999, 404);
  }

  private long[] warehouses(int count) {
    long[] ids = new long[count];
    for (int i = 0; i < count; i++) {
      ids[i] = Long.parseLong(createWarehouse("WH" + i, "AMSTERDAM-001", 10, 0));
    }
    return ids;
  }

  private long assign(long store, long product, long warehouse, int status) {
    var response = given().contentType("application/json")
        .body(Map.of("storeId", store, "productId", product, "warehouseId", warehouse))
        .post("/fulfilment").then().statusCode(status).extract();
    return status == 201 || status == 200 ? response.jsonPath().getLong("id") : -1;
  }
}
