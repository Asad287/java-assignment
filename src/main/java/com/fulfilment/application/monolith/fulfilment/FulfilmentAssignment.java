package com.fulfilment.application.monolith.fulfilment;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.adapters.database.DbWarehouse;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "product_id", "warehouse_id"}))
public class FulfilmentAssignment extends PanacheEntity {
  @ManyToOne(optional = false) @JoinColumn(name = "store_id") public Store store;
  @ManyToOne(optional = false) @JoinColumn(name = "product_id") public Product product;
  @ManyToOne(optional = false) @JoinColumn(name = "warehouse_id") public DbWarehouse warehouse;
}
