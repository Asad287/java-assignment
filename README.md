# Java assignment

API-only application using Java 17+, Maven (or the included Maven wrapper), and Quarkus 3.13.3.
Run the commands below from `java-assignment`.

## Build and test

```sh
./mvnw clean verify
```

On Windows use `./mvnw.cmd clean verify`, or `mvn clean verify` if Maven is installed.
The default tests use an in-memory H2 database and do not need Docker. `verify` runs JUnit
and fails below 80% line or branch coverage. See [TESTING.md](TESTING.md) for PostgreSQL
checks, test scope and report locations.

## Development

```sh
./mvnw quarkus:dev
```

Development uses PostgreSQL through Quarkus Dev Services, which needs Docker.
Dev and test profiles recreate the schema and load demonstration data. With an existing
local development database, set `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME` and `QUARKUS_DATASOURCE_PASSWORD`.

## Run with Docker Compose

```sh
./mvnw clean verify
docker compose up -d --build --wait --wait-timeout 120
```

This starts the JVM application on `localhost:8080` and PostgreSQL on `localhost:15432`,
initializes a new database automatically, and waits for health checks. Data is kept in a
named volume. See [DOCKER.md](DOCKER.md) for Windows commands, verification, logs and shutdown.

## Run the packaged application

Start PostgreSQL, then initialize a new, empty database using
[database/schema.sql](database/schema.sql). For example:

```sh
docker run --name assignment-db -e POSTGRES_USER=quarkus_test -e POSTGRES_PASSWORD=quarkus_test -e POSTGRES_DB=quarkus_test -p 15432:5432 -d postgres:16
docker exec -i assignment-db psql -U quarkus_test -d quarkus_test < database/schema.sql
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

The redirection example uses a POSIX shell. In PowerShell:

```powershell
Get-Content database/schema.sql -Raw | docker exec -i assignment-db psql -U quarkus_test -d quarkus_test
```

Production defaults connect to `localhost:15432/quarkus_test`. Override them with `DB_URL`,
`DB_USERNAME` and `DB_PASSWORD`. Production validates the schema and does not load sample
data or drop tables. The bootstrap script must only be applied to an empty database.

## API

The server base URL is `http://localhost:8080`. Call the endpoints below using an HTTP
client such as Postman or PowerShell. No web UI is served; `/` and `/index.html` return 404.

| Operation | Endpoint | Success |
| --- | --- | --- |
| List active warehouses | `GET /warehouse` | 200 |
| Create warehouse | `POST /warehouse` | 201 |
| Retrieve active warehouse by numeric id | `GET /warehouse/{id}` | 200 |
| Archive warehouse | `DELETE /warehouse/{id}` | 204 |
| Replace by business unit code | `POST /warehouse/{businessUnitCode}/replacement` | 200 |
| Store CRUD and partial update | `/store`, `/store/{id}` | 200/201/204 |
| Product CRUD | `/product`, `/product/{id}` | 200/201/204 |
| List/create/delete fulfillment associations | `/fulfilment`, `/fulfilment/{id}` | 200/201/204 |
| Liveness / database readiness | `/q/health/live`, `/q/health/ready` | 200 |

Warehouse creation example:

```json
{"businessUnitCode":"WH-001","location":"AMSTERDAM-001","capacity":20,"stock":5}
```

Use the returned `id` for GET and DELETE. Replacement accepts the same fields, omitting
`id`, and must preserve stock. The business unit code in its body can be omitted.

Fulfillment example (use ids returned by the relevant create endpoints):

```json
{"storeId":1,"productId":1,"warehouseId":1}
```

Store PATCH accepts `name` and/or `quantityProductsInStock`; omitted fields are preserved.

Read [IMPLEMENTATION.md](IMPLEMENTATION.md) for rules, assumptions and tradeoffs,
[QUESTIONS.md](QUESTIONS.md) for the code questions, and
[the case study](../case-study/CASE_STUDY.md) for the business scenarios.
The GitHub Actions workflow tests H2 and PostgreSQL and uploads test and coverage reports.

The Warehouse interface and DTO are generated from
[src/main/resources/openapi/warehouse-openapi.yaml](src/main/resources/openapi/warehouse-openapi.yaml).
Do not edit generated code. If an IDE does not recognize it, mark
`target/generated-sources/jaxrs` as generated sources.
