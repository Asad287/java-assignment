package com.fulfilment.application.monolith;

import com.fulfilment.application.monolith.fulfilment.FulfilmentAssignment;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import static io.restassured.RestAssured.given;
import java.util.Map;

public abstract class TestDataSupport {
  @Inject protected WarehouseRepository warehouses;
  @Inject protected ProductRepository products;

  @BeforeEach
  void clearData() {
    QuarkusTransaction.requiringNew().run(() -> {
      FulfilmentAssignment.deleteAll();
      warehouses.deleteAll();
      products.deleteAll();
      Store.deleteAll();
    });
  }

  protected String createWarehouse(String code, String location, int capacity, int stock) {
    return given().contentType("application/json")
        .body(Map.of("businessUnitCode", code, "location", location, "capacity", capacity, "stock", stock))
        .post("/warehouse").then().statusCode(201).extract().path("id");
  }

  protected long createProduct(String name) {
    return given().contentType("application/json").body(Map.of("name", name))
        .post("/product").then().statusCode(201).extract().jsonPath().getLong("id");
  }

  protected long createStore(String name) {
    return given().contentType("application/json").body(Map.of("name", name))
        .post("/store").then().statusCode(201).extract().jsonPath().getLong("id");
  }
}
