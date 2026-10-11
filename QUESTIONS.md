# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
I would keep the warehouse ports and use cases: they separate business rules from persistence and make the rules easy to test. Store currently uses Panache active record, Product uses a repository, and Warehouse maps database entities to domain objects. Mixing these styles is not inherently wrong, but transaction ownership and error handling should be consistent.

My first changes would be to put transaction boundaries around complete business operations, remove duplicate exception mappers, and move the legacy notification out of the database transaction. Those changes address correctness before style. The implementation uses a single error mapper and a commit callback with a copy of the persisted store.

Over time I would move Store mutations behind a service and repository if the rules grow. I would not introduce separate domain models for every simple CRUD entity just to match Warehouse. I would also add versioned database migrations and database-level protection for active business unit codes. A shared database lock currently protects cross-row warehouse and fulfillment limits; finer-grained locking would be the next step if write volume increases.
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
The generated Warehouse API makes the YAML contract reviewable independently of the implementation. It is useful when other teams generate clients or need to agree on request fields and response codes before coding. The cost is an extra generation step, dependency management, and less freedom in handler signatures. Generated code must not be edited directly.

Handwritten Product and Store resources are quicker to change and straightforward for small internal APIs. Their risk is that documentation and behavior can drift unless OpenAPI generation and contract tests are part of the build.

I would keep contract-first for Warehouse and use it for externally consumed APIs. For small internal CRUD endpoints, annotations with generated OpenAPI are sufficient. Either approach needs HTTP tests: the original generated Warehouse interface returns a bean, so its handler still needs an explicit 201 status. The spec alone does not guarantee runtime behavior. I updated the Warehouse YAML to describe its validation and conflict responses and kept generated classes out of source control.
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
I would prioritize failures that can lose history, publish uncommitted data, or break capacity limits. Fast JUnit tests cover location lookup and warehouse rules, including missing values, exact boundaries, duplicates, stock mismatch and replacement at a full location. Mockito keeps these tests independent of the database.

Quarkus HTTP tests then exercise persistence, status codes, replacement history, rollback, PATCH with zero stock, and all three fulfillment limits. Store tests check that a legacy callback can read the committed row in a separate transaction and that rollback produces no notification. Concurrent warehouse requests verify that a count check cannot be bypassed by overlapping writes.

The default suite uses H2 for a quick run without Docker. CI also runs it against PostgreSQL because H2 cannot prove PostgreSQL-specific SQL or locking behavior. JaCoCo fails verify below 80% line or branch coverage, excluding only the generated API package. Coverage is a guardrail, not proof of correctness: assertions about saved state, history and external effects matter more than executing getters. I would require a regression test for each bug and keep slow load or recovery tests separate from the pull-request suite.