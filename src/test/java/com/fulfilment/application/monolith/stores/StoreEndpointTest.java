package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fulfilment.application.monolith.TestDataSupport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;

@QuarkusTest
class StoreEndpointTest extends TestDataSupport {

  @InjectMock LegacyStoreManagerGateway gateway;
  @Inject StoreResource resource;
  @Inject StoreLegacySync sync;
  @Inject TransactionSynchronizationRegistry transactions;

  @BeforeEach
  void resetGateway() {
    reset(gateway);
  }

  @Test
  void createNotifiesOnlyAfterCommitWithPersistedIdentity() {
    doAnswer(invocation -> {
      Store snapshot = invocation.getArgument(0);
      assertNotEquals(Status.STATUS_ACTIVE, transactions.getTransactionStatus());
      QuarkusTransaction.requiringNew().run(() -> {
        Store committed = Store.findById(snapshot.id);
        assertNotNull(committed);
        assertEquals(snapshot.name, committed.name);
      });
      return null;
    }).when(gateway).createStoreOnLegacySystem(any());
    long id = createStore("STORE");
    var captured = ArgumentCaptor.forClass(Store.class);
    verify(gateway).createStoreOnLegacySystem(captured.capture());
    assertEquals(id, captured.getValue().id);
    given().get("/store/" + id).then().statusCode(200).body("name", equalTo("STORE"));
    given().get("/store").then().statusCode(200).body("size()", equalTo(1));
  }

  @Test
  void putAndPartialPatchSendCommittedValuesIncludingZeroStock() {
    long id = createStore("STORE");
    reset(gateway);
    given().contentType("application/json").body(Map.of("name", "UPDATED", "quantityProductsInStock", 12))
        .put("/store/" + id).then().statusCode(200).body("quantityProductsInStock", equalTo(12));
    given().contentType("application/json").body(Map.of("quantityProductsInStock", 0))
        .patch("/store/" + id).then().statusCode(200)
        .body("quantityProductsInStock", equalTo(0), "name", equalTo("UPDATED"));
    given().contentType("application/json").body(Map.of("name", "PATCHED"))
        .patch("/store/" + id).then().statusCode(200).body("name", equalTo("PATCHED"));
    var captured = ArgumentCaptor.forClass(Store.class);
    verify(gateway, times(3)).updateStoreOnLegacySystem(captured.capture());
    assertEquals(12, captured.getAllValues().get(0).quantityProductsInStock);
    assertEquals(0, captured.getAllValues().get(1).quantityProductsInStock);
    assertEquals(id, captured.getAllValues().get(2).id);
    given().delete("/store/" + id).then().statusCode(204);
    given().delete("/store/" + id).then().statusCode(404);
  }

  @Test
  void rollbackDoesNotNotifyLegacyOrPersistStore() {
    assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
      resource.create(new Store("ROLLED BACK"));
      throw new IllegalStateException("Simulated rollback");
    }));
    assertEquals(0, Store.count());
    verifyNoInteractions(gateway);
  }

  @Test
  void rolledBackUpdateDoesNotNotifyLegacy() {
    long id = createStore("ORIGINAL");
    reset(gateway);
    assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
      resource.update(id, new Store("ROLLED BACK"));
      throw new IllegalStateException("Simulated rollback");
    }));
    given().get("/store/" + id).then().statusCode(200).body("name", equalTo("ORIGINAL"));
    verifyNoInteractions(gateway);
  }

  @Test
  void callbackUsesSnapshotAndLegacyFailureDoesNotUndoCommit() {
    doThrow(new IllegalStateException("Legacy unavailable")).when(gateway).updateStoreOnLegacySystem(any());
    QuarkusTransaction.requiringNew().run(() -> {
      var store = new Store("SNAPSHOT");
      store.persistAndFlush();
      sync.afterCommit(store, false);
      store.name = "LATER VALUE";
    });
    var captured = ArgumentCaptor.forClass(Store.class);
    verify(gateway).updateStoreOnLegacySystem(captured.capture());
    assertEquals("SNAPSHOT", captured.getValue().name);
    assertEquals(1, Store.count());
  }

  @Test
  void rejectsInvalidDataAndMissingStoresWithoutNotifications() {
    for (String body : new String[] {"null", "{}", "{\"name\":\"\"}",
        "{\"name\":\"STORE\",\"id\":1}", "{\"name\":\"STORE\",\"quantityProductsInStock\":-1}"}) {
      given().contentType("application/json").body(body).post("/store").then().statusCode(422);
    }
    given().contentType("application/json").body(Map.of("name", "x".repeat(41)))
        .post("/store").then().statusCode(422);
    given().contentType("application/json").body(Map.of("name", "VALID"))
        .put("/store/99999").then().statusCode(404);
    given().contentType("application/json").body("null").patch("/store/99999").then().statusCode(400);
    given().contentType("application/json").body("{}").patch("/store/99999").then().statusCode(404);
    verifyNoInteractions(gateway);
  }

  @Test
  void duplicateStoreNameNeverNotifiesForFailedTransaction() {
    createStore("DUPLICATE");
    reset(gateway);
    given().contentType("application/json").body(Map.of("name", "DUPLICATE"))
        .post("/store").then().statusCode(409);
    verifyNoInteractions(gateway);
    assertEquals(1, Store.count());
  }
}
