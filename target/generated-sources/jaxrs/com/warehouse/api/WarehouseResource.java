package com.warehouse.api;

import com.warehouse.api.beans.Warehouse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import java.util.List;

/**
 * A JAX-RS interface. An implementation of this interface must be provided.
 */
@Path("/warehouse")
public interface WarehouseResource {
  @GET
  @Produces("application/json")
  List<Warehouse> listAllWarehousesUnits();

  /**
   * <p>
   * Business unit code, location, capacity and stock are required. Id is assigned
   * by the server.
   * </p>
   * 
   */
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  Warehouse createANewWarehouseUnit(@NotNull Warehouse data);

  @Path("/{id}")
  @GET
  @Produces("application/json")
  Warehouse getAWarehouseUnitByID(@PathParam("id") @Pattern(regexp = "^[1-9][0-9]*$") String id);

  @Path("/{id}")
  @DELETE
  void archiveAWarehouseUnitByID(@PathParam("id") @Pattern(regexp = "^[1-9][0-9]*$") String id);

  /**
   * <p>
   * Archives the active warehouse and creates a new row with the same business
   * unit code in one transaction. Stock must match the previous warehouse. A body
   * code may be omitted, but if supplied it must match the path. Id must be
   * omitted.
   * </p>
   * 
   */
  @Path("/{businessUnitCode}/replacement")
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  Warehouse replaceTheCurrentActiveWarehouse(@PathParam("businessUnitCode") String businessUnitCode,
      @NotNull Warehouse data);
}
