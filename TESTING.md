# Testing

## Running the suite

From `java-assignment`:

```sh
./mvnw clean verify
```

Windows: `./mvnw.cmd clean verify`. An installed Maven also works: `mvn clean verify`.
JUnit 5 runs both plain unit tests and Quarkus HTTP/persistence tests. The default database
is H2 in PostgreSQL compatibility mode, recreated for each test run. HTTP test classes
clear their data before each test and use returned ids instead of depending on test order.
Docker is not required for the default suite.

## Coverage

JaCoCo measures both plain JUnit and Quarkus tests in `target/jacoco.exec`. The Maven plugin
excludes Quarkus' test class loader from its agent, and the Quarkus JaCoCo extension
instruments those classes instead. This follows the
[Quarkus coverage guide](https://quarkus.io/guides/tests-with-coverage/).

`verify` generates `target/site/jacoco/index.html`, `jacoco.xml` and `jacoco.csv`, then
checks minimum **80% line coverage and 80% branch coverage**. Only `com/warehouse/api/**`
is excluded because those interfaces and DTOs are generated from OpenAPI. Application
models, repositories, use cases and error handling remain included. Use `clean verify`
for an independent measurement; plain `verify` may append to an earlier execution file.

Local verification on 11 October 2026, using JDK 21.0.10 and Maven 3.9.12:

- `mvn clean verify`: **BUILD SUCCESS**.
- **68 tests**, zero failures, zero errors, zero skipped.
- Line coverage: **96.79%**.
- Branch coverage: **98.31%**.
- Instruction coverage: **97.75%**.

The H2 suite and a separate PostgreSQL 16.15 run both passed all 68 tests with these
coverage figures. The generated HTML
report is available at [target/site/jacoco/index.html](target/site/jacoco/index.html)
after the build.

## Cases covered

| Area | Positive cases | Negative/error cases |
| --- | --- | --- |
| Location | All configured locations; returned values cannot change the catalog | Unknown, null, empty and differently cased identifiers |
| Create warehouse | Exact stock/capacity boundaries; aggregate location capacity | Duplicate active code; missing fields; unknown location; non-positive capacity; negative or excessive stock; count/capacity limits |
| Replace warehouse | Same code with a new id; old timestamps and stock retained; replacement at a full location; movement to another location | Missing active warehouse; mismatched path/body code; changed stock; insufficient capacity; destination limits; rollback after both writes |
| Archive warehouse | Hidden from active API; row and stock retained; slot/code can be reused | Unknown or already archived id; malformed ids |
| Concurrency | Overlapping creation requests and fulfillment assignments | Only one warehouse creation succeeds at a single-slot location; concurrent assignments cannot exceed the product/store limit |
| Store sync | Create/PUT/PATCH send persisted id and values after commit; callback reads committed row in another transaction | Rolled-back create/update send nothing; duplicate constraint failure sends nothing; legacy failure leaves committed data intact |
| Store PATCH | Omitted fields preserved; explicit zero accepted | Missing body, invalid data, unknown store |
| Product | CRUD including description, price and stock | Missing/invalid data, negative values, invalid price precision, duplicate name, unknown id |
| Fulfillment | Create/list/delete; identical request is idempotent; existing warehouses/product types do not consume another distinct slot | Two warehouses per product/store; three per store; five product types per warehouse; missing/invalid/unknown/archived ids |
| Fulfillment lifecycle | Historical associations retained after warehouse replacement; store/product deletion cleans associations | Archived warehouse cannot receive a new association |
| Errors and health | JSON status/message; liveness and database readiness | Internal exception text hidden from API responses |

Expected duplicate-name and simulated legacy-failure tests emit error logs. These are
intentional scenarios; the assertions verify response codes and persisted state.

## PostgreSQL verification

Use a **disposable test database**: this profile drops and recreates its schema.
With PostgreSQL available on port 5432, configure these environment variables:

```sh
TEST_DB_KIND=postgresql \
TEST_DB_URL=jdbc:postgresql://localhost:5432/assignment_test \
TEST_DB_USERNAME=assignment TEST_DB_PASSWORD=assignment \
./mvnw clean verify
```

PowerShell:

```powershell
$env:TEST_DB_KIND = 'postgresql'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/assignment_test'
$env:TEST_DB_USERNAME = 'assignment'
$env:TEST_DB_PASSWORD = 'assignment'
./mvnw.cmd clean verify
```

Remove those variables afterward to return to H2. H2 compatibility does not establish
PostgreSQL locking or dialect correctness. The GitHub Actions workflow runs the suite
on Java 17/H2 and Java 21/PostgreSQL 16. The PostgreSQL job also initializes a separate
empty database with `database/schema.sql`, starts the packaged application with production
schema validation, checks readiness, and creates a warehouse through HTTP.

Local Docker verification on 11 October 2026 also ran the packaged application against a
separate PostgreSQL database initialized with `database/schema.sql`. Production schema
validation succeeded, both containers were healthy, and 37 API/database smoke assertions
passed. Archived warehouse history remained after an application restart. The commands and
repeatable smoke script are described in [DOCKER.md](DOCKER.md).

The GitHub Actions workflow itself has not been run after upload. Native-image execution
and load testing were not run.

## Maintaining the tests

Add a regression test for each bug, especially history loss or notification ordering.
Review uncovered branches before changing the threshold. Keep assertions on behavior and
stored state; more tests that only execute methods do not necessarily improve confidence.
Slow performance and recovery tests can run separately from the pull-request build.
