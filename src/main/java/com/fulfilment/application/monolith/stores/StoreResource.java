package com.fulfilment.application.monolith.stores;

import com.fulfilment.application.monolith.fulfilment.FulfilmentAssignment;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("store")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class StoreResource {

  @Inject StoreLegacySync legacySync;
  @Inject WarehouseRepository warehouses;

  @GET
  public List<Store> get() {
    return Store.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Store getSingle(Long id) {
    Store entity = Store.findById(id);
    if (entity == null) {
      throw new NotFoundException("Store with id of " + id + " does not exist.");
    }
    return entity;
  }

  @POST
  @Transactional
  public Response create(Store store) {
    validate(store);
    if (store.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }
    store.persistAndFlush();
    legacySync.afterCommit(store, true);
    return Response.status(201).entity(store).build();
  }

  @PUT
  @Path("{id}")
  @Transactional
  public Store update(Long id, Store updatedStore) {
    validate(updatedStore);
    Store entity = getSingle(id);
    entity.name = updatedStore.name;
    entity.quantityProductsInStock = updatedStore.quantityProductsInStock;
    entity.flush();
    legacySync.afterCommit(entity, false);
    return entity;
  }

  public record StorePatch(String name, Integer quantityProductsInStock) {}

  @PATCH
  @Path("{id}")
  @Transactional
  public Store patch(Long id, StorePatch patch) {
    if (patch == null) {
      throw new BadRequestException("Store body is required");
    }
    Store entity = getSingle(id);
    if (patch.name() != null) {
      entity.name = patch.name();
    }
    if (patch.quantityProductsInStock() != null) {
      entity.quantityProductsInStock = patch.quantityProductsInStock();
    }
    validate(entity);
    entity.flush();
    legacySync.afterCommit(entity, false);
    return entity;
  }

  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    warehouses.lock();
    Store entity = getSingle(id);
    FulfilmentAssignment.delete("store.id", id);
    entity.delete();
    return Response.noContent().build();
  }

  private void validate(Store store) {
    if (store == null || store.name == null || store.name.isBlank() || store.name.length() > 40
        || store.quantityProductsInStock < 0) {
      throw new WebApplicationException("Store name is required (maximum 40 characters) and stock must be non-negative.", 422);
    }
  }
}
