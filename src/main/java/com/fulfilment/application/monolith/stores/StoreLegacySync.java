package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StoreLegacySync {

  private static final Logger LOG = Logger.getLogger(StoreLegacySync.class);

  @Inject TransactionSynchronizationRegistry transactions;
  @Inject LegacyStoreManagerGateway gateway;

  public void afterCommit(Store store, boolean created) {
    // The callback must not depend on a managed entity or a later request mutation.
    var snapshot = new Store(store.name);
    snapshot.id = store.id;
    snapshot.quantityProductsInStock = store.quantityProductsInStock;
    transactions.registerInterposedSynchronization(new Synchronization() {
      @Override
      public void beforeCompletion() {}

      @Override
      public void afterCompletion(int status) {
        if (status != Status.STATUS_COMMITTED) {
          return;
        }
        try {
          if (created) {
            gateway.createStoreOnLegacySystem(snapshot);
          } else {
            gateway.updateStoreOnLegacySystem(snapshot);
          }
        } catch (RuntimeException e) {
          LOG.errorf(e, "Legacy synchronization failed for committed store %s", snapshot.id);
        }
      }
    });
  }
}
