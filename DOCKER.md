# Running and verifying with Docker

Run these commands from `java-assignment`. Docker Desktop must be running with Linux
containers. Building the application also requires Java 17+ and Maven, or the included
Maven wrapper. The supplied JVM Dockerfile packages the Maven output; it does not compile
the source inside Docker.

## Start the application

```powershell
./mvnw.cmd clean verify
docker compose up -d --build --wait --wait-timeout 120
docker compose ps
```

An installed Maven can replace `./mvnw.cmd` with `mvn`. On Linux/macOS use `./mvnw`.
Repeat the build and Compose commands after changing application source.

If Docker Desktop is installed per user on Windows but `docker` is not on this terminal's
PATH, add its CLI directory for the current session:

```powershell
$dockerBin = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'
$env:PATH = "$dockerBin;$env:PATH"
docker version
```

| Service | Purpose | Host address |
| --- | --- | --- |
| `application` | Quarkus production profile, Java 17 JVM image | `http://localhost:8080` |
| `database` | PostgreSQL 16 application data, named volume | `localhost:15432` |
| `test-db` | Separate database enabled by the `verification` profile | `localhost:15433` |

Ports are bound to the local machine. The application reaches PostgreSQL over the Compose
network using `database:5432`; its URL is independent of the host's port 15432.
The credentials `assignment` / `assignment` are local development credentials.
The test database is named `assignment_test`; the application database is `assignment`.

The application image is `fcs-assignment:local`. Its build uses
[Dockerfile.jvm](src/main/docker/Dockerfile.jvm) and the `target/quarkus-app` directory.
[.dockerignore](.dockerignore) limits the build context to those inputs.

PostgreSQL applies [database/schema.sql](database/schema.sql) only when its data directory
is first initialized. This creates the tables, sequences, indexes and shared warehouse
lock row. Subsequent starts reuse the named `postgres-data` volume. Quarkus validates the
schema on startup and does not insert demonstration data in production.

## Check the running application

- Warehouse API: [http://localhost:8080/warehouse](http://localhost:8080/warehouse).
- Store API: [http://localhost:8080/store](http://localhost:8080/store).
- Product API: [http://localhost:8080/product](http://localhost:8080/product).
- Fulfillment API: [http://localhost:8080/fulfilment](http://localhost:8080/fulfilment).
- Liveness: [http://localhost:8080/q/health/live](http://localhost:8080/q/health/live).
- Database readiness: [http://localhost:8080/q/health/ready](http://localhost:8080/q/health/ready).

This application serves JSON APIs and health endpoints. There is no web UI; `/` and
`/index.html` return 404. Use an HTTP client for writes and see [README.md](README.md)
for the REST operations.

Run the repeatable PowerShell smoke check:

```powershell
./scripts/verify-docker.ps1
```

It checks that UI URLs return 404, all four API collections respond, health, Store PATCH,
Product creation, Warehouse validation/replacement, archived history in PostgreSQL, and fulfillment association
behavior. Each run uses unique names. It deletes its own store/product records and their
associations afterward; its two warehouse rows remain archived, following the application's
history rules. It does not delete other application data.

The result is written to `target/docker-verification.json`. The script accepts `-BaseUrl`
and `-DockerExecutable` overrides; database checks use this project's Compose `database`
service, so the URL should refer to the application connected to that database.

## Run all JUnit tests against PostgreSQL

The test profile drops and recreates its schema. Use the separate `test-db` service, not
the application database. The application can remain running while these tests execute.

```powershell
docker compose --profile verification up -d --wait --wait-timeout 120 test-db
$env:TEST_DB_KIND = 'postgresql'
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:15433/assignment_test'
$env:TEST_DB_USERNAME = 'assignment'
$env:TEST_DB_PASSWORD = 'assignment'
try {
    ./mvnw.cmd clean verify
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL verification failed' }
} finally {
    Remove-Item Env:TEST_DB_KIND, Env:TEST_DB_URL, Env:TEST_DB_USERNAME, Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
    docker compose --profile verification stop test-db
}
```

Without those variables, tests use H2. [TESTING.md](TESTING.md) describes the scenarios and
JaCoCo gates. The coverage report is `target/site/jacoco/index.html`, and JUnit XML results
are under `target/surefire-reports`. `clean` removes earlier reports, including the smoke
report, so run the smoke script again if a fresh report is needed.

## Stop, restart and inspect

```powershell
docker compose logs --tail 100 application database
docker compose restart application
docker compose up -d --wait --wait-timeout 120
docker compose down
```

`down` removes the application containers and network but retains the database volume.
Start again with `docker compose up -d --wait`. If the optional test container still exists,
`docker compose --profile verification down` stops and removes it too. Do not add `-v`
when retaining application data: that option deletes the named database volume.

The initial SQL is not reapplied to an existing volume. Schema changes for an existing
installation require a migration; rebuilding the image alone does not migrate the database.

## Verification recorded on 11 October 2026

Docker Desktop 4.94.0 / Engine 29.8.2 ran the JVM application and PostgreSQL 16.15 locally.
The packaged application started with the production profile and passed schema validation.

- **68 JUnit tests passed** against PostgreSQL, with zero failures, errors or skips.
- JaCoCo: **96.79% lines**, **98.31% branches**, **97.75% instructions**; both 80% gates passed.
- **37 smoke assertions passed**, including absent UI routes, API collections, database history checks and expected errors.
- Application and database containers were healthy; liveness and readiness returned `UP`.
- After restarting the application, readiness was `UP`, API collections returned HTTP 200,
  `/` and `/index.html` returned 404, and archived warehouse rows remained in PostgreSQL.

The application and its database were left running. The optional test database was stopped.
GitHub Actions, native-image execution and load tests were not run as part of this check.
