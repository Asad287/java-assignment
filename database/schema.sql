-- Bootstrap for a new, empty PostgreSQL database.
BEGIN;

create sequence Store_SEQ start with 1 increment by 50;
create sequence Product_SEQ start with 1 increment by 50;
create sequence warehouse_SEQ start with 1 increment by 50;
create sequence FulfilmentAssignment_SEQ start with 1 increment by 50;

create table Store (
  id bigint primary key,
  name varchar(40) unique,
  quantityProductsInStock integer not null
);

create table Product (
  id bigint primary key,
  name varchar(40) unique,
  description varchar(255),
  price numeric(10, 2),
  stock integer not null
);

create table warehouse (
  id bigint primary key,
  businessUnitCode varchar(255),
  location varchar(255),
  capacity integer,
  stock integer,
  createdAt timestamp(6),
  archivedAt timestamp(6)
);

create table WarehouseLock (
  id bigint primary key
);

create table FulfilmentAssignment (
  id bigint primary key,
  store_id bigint not null references Store(id),
  product_id bigint not null references Product(id),
  warehouse_id bigint not null references warehouse(id),
  unique (store_id, product_id, warehouse_id)
);

-- Business unit codes remain reusable by archived versions.
create unique index warehouse_active_code
  on warehouse (businessUnitCode) where archivedAt is null;
create index warehouse_active_location
  on warehouse (location) where archivedAt is null;

INSERT INTO WarehouseLock(id) VALUES (1);

COMMIT;
