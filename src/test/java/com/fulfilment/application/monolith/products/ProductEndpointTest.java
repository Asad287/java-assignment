package com.fulfilment.application.monolith.products;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import com.fulfilment.application.monolith.TestDataSupport;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.Map;

@QuarkusTest
class ProductEndpointTest extends TestDataSupport {

  @Test
  void createsListsReadsUpdatesAndDeletesProduct() {
    long id = createProduct("BOOK");
    given().get("/product").then().statusCode(200).body("name", hasItem("BOOK"));
    given().get("/product/" + id).then().statusCode(200).body("name", equalTo("BOOK"));
    given().contentType("application/json")
        .body(Map.of("name", "BOOK UPDATED", "description", "Paperback", "price", 12.50, "stock", 5))
        .put("/product/" + id).then().statusCode(200).body("stock", equalTo(5), "description", equalTo("Paperback"));
    given().delete("/product/" + id).then().statusCode(204);
    given().get("/product/" + id).then().statusCode(404);
    given().delete("/product/" + id).then().statusCode(404);
  }

  @Test
  void rejectsInvalidProductDataAndMissingUpdate() {
    for (String body : new String[] {"null", "{}", "{\"name\":\"\"}",
        "{\"name\":\"BOOK\",\"id\":1}", "{\"name\":\"BOOK\",\"stock\":-1}",
        "{\"name\":\"BOOK\",\"price\":-1}", "{\"name\":\"BOOK\",\"price\":1.234}",
        "{\"name\":\"BOOK\",\"price\":100000000}"}) {
      given().contentType("application/json").body(body).post("/product").then().statusCode(422);
    }
    given().contentType("application/json").body(Map.of("name", "VALID"))
        .put("/product/99999").then().statusCode(404);
    given().contentType("application/json").body("{}")
        .put("/product/99999").then().statusCode(422);
    given().contentType("application/json").body(Map.of("name", "x".repeat(41)))
        .post("/product").then().statusCode(422);
  }

  @Test
  void duplicateNameReturnsConflictWithoutAddingAnotherProduct() {
    createProduct("DUPLICATE");
    given().contentType("application/json").body(Map.of("name", "DUPLICATE"))
        .post("/product").then().statusCode(409);
    given().get("/product").then().statusCode(200).body("size()", equalTo(1));
  }
}
