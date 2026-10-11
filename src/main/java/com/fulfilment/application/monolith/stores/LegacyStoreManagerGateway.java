package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LegacyStoreManagerGateway {

  private static final Logger LOG = Logger.getLogger(LegacyStoreManagerGateway.class);

  public void createStoreOnLegacySystem(Store store) {
    writeToFile(store, "created");
  }

  public void updateStoreOnLegacySystem(Store store) {
    writeToFile(store, "updated");
  }

  private void writeToFile(Store store, String operation) {
    Path file = null;
    try {
      file = Files.createTempFile("legacy-store-", ".txt");
      Files.writeString(file, "Store " + operation + ". [ name = " + store.name
          + " ] [ items on stock = " + store.quantityProductsInStock + " ]");
      LOG.infof("Legacy store %s: id=%s", operation, store.id);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to synchronize store " + store.id, e);
    } finally {
      if (file != null) {
        try {
          Files.deleteIfExists(file);
        } catch (IOException e) {
          LOG.warn("Unable to delete legacy simulation file", e);
        }
      }
    }
  }
}
