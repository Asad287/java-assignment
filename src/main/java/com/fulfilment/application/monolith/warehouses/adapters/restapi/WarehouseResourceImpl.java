package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.adapters.database.WarehouseRepository;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import java.util.List;
import org.jboss.resteasy.reactive.ResponseStatus;

@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject WarehouseRepository warehouseRepository;
  @Inject CreateWarehouseOperation createWarehouse;
  @Inject ReplaceWarehouseOperation replaceWarehouse;
  @Inject ArchiveWarehouseOperation archiveWarehouse;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseRepository.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  @Override
  @POST
  @Consumes("application/json")
  @Produces("application/json")
  @ResponseStatus(201)
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    var warehouse = toDomain(data);
    createWarehouse.create(warehouse);
    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    var warehouse = warehouseRepository.findActiveById(parseId(id));
    if (warehouse == null) {
      throw new NotFoundException("Active warehouse does not exist");
    }
    return toWarehouseResponse(warehouse);
  }

  @Override
  public void archiveAWarehouseUnitByID(String id) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.id = parseId(id);
    archiveWarehouse.archive(warehouse);
  }

  @Override
  public Warehouse replaceTheCurrentActiveWarehouse(String businessUnitCode, @NotNull Warehouse data) {
    var warehouse = toDomain(data);
    if (warehouse.businessUnitCode != null && !businessUnitCode.equals(warehouse.businessUnitCode)) {
      throw new BadRequestException("Business unit code in the body must match the path");
    }
    warehouse.businessUnitCode = businessUnitCode;
    replaceWarehouse.replace(warehouse);
    return toWarehouseResponse(warehouse);
  }

  private Long parseId(String id) {
    try {
      long value = Long.parseLong(id);
      if (value <= 0) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException e) {
      throw new BadRequestException("Warehouse id must be a positive integer");
    }
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toDomain(Warehouse data) {
    if (data == null) {
      throw new BadRequestException("Warehouse body is required");
    }
    if (data.getId() != null) {
      throw new BadRequestException("Id must not be set on a new warehouse");
    }
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();
    return warehouse;
  }

  private Warehouse toWarehouseResponse(com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setId(warehouse.id.toString());
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);
    return response;
  }
}
