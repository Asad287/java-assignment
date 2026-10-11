package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public void lock() {
    var lock = getEntityManager().find(WarehouseLock.class, 1L, LockModeType.PESSIMISTIC_WRITE);
    if (lock == null) {
      throw new IllegalStateException("Warehouse lock row is missing");
    }
  }

  @Override
  public List<Warehouse> getAll() {
    return list("archivedAt is null order by id").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    var entity = new DbWarehouse();
    copy(warehouse, entity);
    persistAndFlush(entity);
    warehouse.id = entity.id;
  }

  @Override
  public void update(Warehouse warehouse) {
    var entity = findById(warehouse.id);
    if (entity == null) {
      throw new IllegalStateException("Warehouse no longer exists: " + warehouse.id);
    }
    copy(warehouse, entity);
    flush();
  }

  @Override
  public void remove(Warehouse warehouse) {
    warehouse.archivedAt = LocalDateTime.now();
    update(warehouse);
  }

  @Override
  public Warehouse findActiveById(Long id) {
    var entity = find("id = ?1 and archivedAt is null", id).firstResult();
    return entity == null ? null : entity.toWarehouse();
  }

  @Override
  public Warehouse findByBusinessUnitCode(String code) {
    var entity = find("businessUnitCode = ?1 and archivedAt is null", code).firstResult();
    return entity == null ? null : entity.toWarehouse();
  }

  private void copy(Warehouse warehouse, DbWarehouse entity) {
    entity.businessUnitCode = warehouse.businessUnitCode;
    entity.location = warehouse.location;
    entity.capacity = warehouse.capacity;
    entity.stock = warehouse.stock;
    entity.createdAt = warehouse.createdAt;
    entity.archivedAt = warehouse.archivedAt;
  }
}
