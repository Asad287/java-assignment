package com.fulfilment.application.monolith.common;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointTest {
  @Test
  void reportsLivenessAndDatabaseReadiness() {
    given().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
    given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
  }
}
