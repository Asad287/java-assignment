package com.fulfilment.application.monolith.products;

import com.fulfilment.application.monolith.fulfilment.FulfilmentAssignment;
import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.math.BigDecimal;

@Path("product")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class ProductResource {

  @Inject ProductRepository productRepository;
  @Inject WarehouseRepository warehouses;

  @GET
  public List<Product> get() {
    return productRepository.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Product getSingle(Long id) {
    Product entity = productRepository.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Product with id of " + id + " does not exist.", 404);
    }
    return entity;
  }

  @POST
  @Transactional
  public Response create(Product product) {
    validate(product);
    if (product.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    productRepository.persistAndFlush(product);
    return Response.ok(product).status(201).build();
  }

  @PUT
  @Path("{id}")
  @Transactional
  public Product update(Long id, Product product) {
    validate(product);

    Product entity = productRepository.findById(id);

    if (entity == null) {
      throw new WebApplicationException("Product with id of " + id + " does not exist.", 404);
    }

    entity.name = product.name;
    entity.description = product.description;
    entity.price = product.price;
    entity.stock = product.stock;

    productRepository.flush();

    return entity;
  }

  @DELETE
  @Path("{id}")
  @Transactional
  public Response delete(Long id) {
    warehouses.lock();
    Product entity = productRepository.findById(id);
    if (entity == null) {
      throw new WebApplicationException("Product with id of " + id + " does not exist.", 404);
    }
    FulfilmentAssignment.delete("product.id", id);
    productRepository.delete(entity);
    return Response.status(204).build();
  }

  private void validate(Product product) {
    if (product == null || product.name == null || product.name.isBlank() || product.name.length() > 40
        || product.stock < 0 || (product.price != null && (product.price.signum() < 0
        || product.price.compareTo(new BigDecimal("99999999.99")) > 0
        || product.price.stripTrailingZeros().scale() > 2))) {
      throw new WebApplicationException("Product requires a name (maximum 40 characters), non-negative stock and a valid price.", 422);
    }
  }
}
