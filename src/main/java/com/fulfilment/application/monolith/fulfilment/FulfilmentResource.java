package com.fulfilment.application.monolith.fulfilment;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
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
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("fulfilment")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FulfilmentResource {

  @Inject WarehouseRepository warehouses;
  @Inject ProductRepository products;

  public record AssignmentRequest(Long storeId, Long productId, Long warehouseId) {}
  public record AssignmentResponse(Long id, Long storeId, Long productId, Long warehouseId) {}

  @GET
  public List<AssignmentResponse> list() {
    return FulfilmentAssignment.<FulfilmentAssignment>list("warehouse.archivedAt is null order by id")
        .stream().map(this::response).toList();
  }

  @POST
  @Transactional
  public Response create(AssignmentRequest request) {
    if (request == null || request.storeId() == null || request.productId() == null
        || request.warehouseId() == null || request.storeId() <= 0 || request.productId() <= 0
        || request.warehouseId() <= 0) {
      throw new BadRequestException("Positive store, product and warehouse ids are required");
    }
    warehouses.lock();
    Store store = Store.findById(request.storeId());
    Product product = products.findById(request.productId());
    var warehouse = warehouses.findById(request.warehouseId());
    if (store == null || product == null || warehouse == null || warehouse.archivedAt != null) {
      throw new NotFoundException("Store, product or active warehouse does not exist");
    }
    FulfilmentAssignment existing = FulfilmentAssignment.find(
        "store.id = ?1 and product.id = ?2 and warehouse.id = ?3",
        store.id, product.id, warehouse.id).firstResult();
    if (existing != null) {
      return Response.ok(response(existing)).build();
    }
    var assignments = FulfilmentAssignment.<FulfilmentAssignment>list("warehouse.archivedAt is null");
    long productWarehouses = assignments.stream()
        .filter(a -> a.store.id.equals(store.id) && a.product.id.equals(product.id))
        .map(a -> a.warehouse.id).distinct().count();
    if (productWarehouses >= 2) {
      throw new WebApplicationException("A product can use at most two warehouses per store", 409);
    }
    var storeWarehouses = assignments.stream().filter(a -> a.store.id.equals(store.id))
        .map(a -> a.warehouse.id).distinct().toList();
    if (!storeWarehouses.contains(warehouse.id) && storeWarehouses.size() >= 3) {
      throw new WebApplicationException("A store can use at most three warehouses", 409);
    }
    var warehouseProducts = assignments.stream().filter(a -> a.warehouse.id.equals(warehouse.id))
        .map(a -> a.product.id).distinct().toList();
    if (!warehouseProducts.contains(product.id) && warehouseProducts.size() >= 5) {
      throw new WebApplicationException("A warehouse can store at most five product types", 409);
    }
    var assignment = new FulfilmentAssignment();
    assignment.store = store;
    assignment.product = product;
    assignment.warehouse = warehouse;
    assignment.persistAndFlush();
    return Response.status(201).entity(response(assignment)).build();
  }

  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    warehouses.lock();
    if (!FulfilmentAssignment.deleteById(id)) {
      throw new NotFoundException("Fulfilment assignment does not exist");
    }
    return Response.noContent().build();
  }

  private AssignmentResponse response(FulfilmentAssignment assignment) {
    return new AssignmentResponse(assignment.id, assignment.store.id, assignment.product.id,
        assignment.warehouse.id);
  }
}
