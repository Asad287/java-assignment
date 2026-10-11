# Implementation decisions

## Warehouse identity and history

`id` identifies one physical warehouse row. `businessUnitCode` identifies its business unit
and can be shared by historical versions. GET and DELETE use the numeric row id;
replacement uses the business unit code. Lists and lookups expose active rows only.
An archived id returns 404. Creating with an already active code returns 409; an archived
code may be reused. This interpretation allows the replacement behavior in the briefing.

Create assigns a server timestamp. Archive updates `archivedAt` without deleting anything.
Replacement validates the incoming warehouse, archives the old row and inserts the new
one in a single transaction. Its stock must match the original and its capacity must hold
that stock. A failure before commit rolls back both writes. Clients cannot supply a row id.
For replacement the path is authoritative: an omitted code in the body is accepted, but a
conflicting body code returns 400.

## Location limits and concurrency

`Location.maxCapacity` is the sum of capacities of active warehouses at that location,
as stated in the supplied model. Archived rows do not consume capacity or warehouse slots.
A replacement excludes its own predecessor from the calculation, allowing replacement at
an otherwise full location. Moving to another location checks that destination's limits.
Capacity must be positive; stock must be non-negative and no greater than capacity.

Checking a count and then inserting without coordination allows concurrent requests to
both pass. Warehouse and fulfillment mutations take a pessimistic database lock on the
single `WarehouseLock` row, held until the surrounding transaction completes. Store and
Product deletes take the same lock before removing their fulfillment associations.
This also works across application instances using the same database. The singleton lock
is deliberately simple for this assignment; at higher volume, lock by location and business
unit with a consistent ordering. Direct SQL writers must follow the same protocol.
Production should also enforce active-code uniqueness with a PostgreSQL partial index.

The starter seed gave ZWOLLE-001 capacity 100 despite a location limit of 40. It now uses
40, so the demonstration data satisfies the implemented rules.

## Store synchronization

Create, PUT and PATCH register a transaction synchronization callback with a copy of the
persisted store's id, name and stock. The gateway is called only when the completion status
is `STATUS_COMMITTED`. A rollback skips the callback. Copying the data keeps the callback
independent of a detached entity or later mutation. PATCH uses nullable fields so omitted
stock stays unchanged and an explicit zero is applied.

The existing gateway simulates an integration by writing and deleting a temporary file.
It now uses structured logging, UTF-8 text and explicit I/O error handling. A callback
failure is logged with the committed store id; it cannot undo a completed database commit.
This guarantees **commit ordering**, not durable delivery. A process crash after commit or
a legacy outage can lose a notification. A production integration should use a transactional
outbox, an idempotent consumer and monitored retries. Delete has no legacy operation in the
supplied gateway, so its behavior remains database deletion.

## Fulfillment associations

`POST /fulfilment` associates one store, product and active warehouse. The checks count
**distinct** warehouses and product types, across all applicable associations:

- A product uses at most two warehouses for each store.
- A store uses at most three warehouses across all its products.
- A warehouse holds at most five product types across all its stores.

A repeated identical association returns its existing row with 200; a new one returns 201.
The database also enforces uniqueness of the store/product/warehouse tuple. Unknown or
archived entities return 404, missing or non-positive ids return 400, and limit violations
return 409. DELETE removes an association and releases its limits.

Associations reference the physical warehouse id. Archiving or replacing a warehouse keeps
its old associations as history, but removes them from active lists and counts. A replacement
starts without associations and must be assigned explicitly. The assignment does not specify
automatic transfer; this avoids silently routing orders to a new physical site. Deleting a
store or product removes its associations before deleting the entity.

## API errors and operations

One exception mapper produces `{ "code": 400, "error": "..." }` responses. Warehouse domain
errors remain independent of HTTP types; the mapper converts invalid/conflict/not-found
reasons to 400/409/404. Store and Product retain 422 for invalid entity input. Unexpected
errors are logged and return a generic message without database or exception internals.

Health endpoints are `/q/health/live` and `/q/health/ready`; readiness includes the datasource.
The production profile validates its schema instead of dropping tables on startup. The
bootstrap SQL under `database/` creates an empty PostgreSQL schema and the required lock row.
It is a one-time setup for a new database, not an upgrade script for an existing installation.
Versioned migrations, authentication and durable integration delivery are the next production
steps. Cost tracking and financial integrations are discussed in the case study; they are
outside the implemented warehouse-management assignment.
