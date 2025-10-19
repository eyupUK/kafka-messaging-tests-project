# Kafka Messaging Tests (Avro + Schema Registry + DLQ)

An opinionated, ready-to-run Maven test module that uses Testcontainers to run an isolated integration and end-to-end environment for an **_API Gateway → Kafka → Microservice → PostgreSQL_** flow.

This repository focuses on automated verification of messaging behavior, Avro schema compatibility/evolution, consumer idempotency, and DLQ (dead-letter) handling — all within fast, reproducible tests.

## Key features
- Isolated infrastructure via Testcontainers: Kafka, Confluent Schema Registry, PostgreSQL
- Avro (writer/reader) schemas with compatibility and evolution tests
- Dead-letter queue (DLQ) behavior and error header assertions
- Idempotency tests to avoid duplicate DB records
- End-to-end REST → Kafka → DB verification
- CI-friendly: GitHub Actions workflows and Allure reporting


## Tech stack
- Java 21
- Maven 3.9+
- Spring Boot (test-scoped Spring Boot application used by tests)
- Spring Kafka (consumer/producer, error handling, DefaultErrorHandler)
- Apache Avro (schema definitions and generated Java classes)
- Confluent Schema Registry (embedded/managed through Testcontainers)
- Testcontainers (Kafka, Schema Registry, PostgreSQL)
- PostgreSQL (HikariCP connection pooling)
- JUnit 5 (integration tests)
- Awaitility (async assertions)
- Allure (optional test reporting)


## What this project verifies
- Integration: message production → Kafka topic → consumer → database persistence
- Idempotency: consumer logic + DB constraints prevent duplicate inserts
- DLQ behavior: failing/invalid messages are published to a DLQ topic with diagnostic headers
- Schema compatibility: programmatic checks for BACKWARD / FULL compatibility modes in Schema Registry
- Schema evolution: reader/writer compatibility between Avro v1 and v2 schemas
- End-to-end flow: REST request results in a Kafka message and a corresponding DB row


## Repository layout (tests module)
- `kafka-messaging-tests/`
  - `pom.xml` — module build configuration and test profiles
  - `src/test/java/org/example/tests/`
    - `TestApp.java` — test-scoped Spring Boot application (REST → Kafka producer; consumer → DB)
    - `AvroKafkaTestConfig.java` — Kafka topics, Avro SerDes, DLQ wiring and error handler
    - `KafkaConfigTest.java` — Testcontainers orchestration (Kafka, Schema Registry, Postgres)
    - `ApiToKafkaE2E.java` — E2E test that exercises REST → Kafka → DB
    - `KafkaConsumerIT.java` — idempotency integration test
    - `DlqAssertionIT.java` — ensures failing messages land on DLQ with expected headers
    - `SchemaCompatibilityIT.java` — schema registry compatibility checks (BACKWARD, FULL)
    - `SchemaEvolutionIT.java` — reader/writer evolution tests between v1 and v2
  - `src/test/avro/` — avsc files for `v1` and `v2` schemas
  - `src/test/resources/application-test.yml` — test properties (topics, registry URL, DB config)


## Quickstart — run the tests

From the repository root (where the parent `pom.xml` lives):

```bash
# Full Kafka integration tests (Kafka + Schema Registry + Postgres)
mvn -pl kafka-messaging-tests -P kafka-it verify

# End-to-end tests (REST -> Kafka -> DB)
mvn -pl kafka-messaging-tests -P e2e verify

# Run only Avro schema compatibility & evolution tests
mvn -pl kafka-messaging-tests -P schema-compat verify
```

## Notes
- Docker must be running (Testcontainers will start/stop the containers).
- Tests are designed for CI: they start and tear down containers automatically.


### Allure (optional)
- Allure results are written to: `kafka-messaging-tests/target/allure-results`
- If Allure CLI is installed: `allure serve kafka-messaging-tests/target/allure-results`


## Profiles & test scopes

| Profile | Purpose | Command |
|---|---:|---|
| `kafka-it` | Integration tests that require Kafka + DB | `mvn -pl kafka-messaging-tests -P kafka-it verify` |
| `e2e` | End-to-end REST → Kafka → DB tests | `mvn -pl kafka-messaging-tests -P e2e verify` |
| `schema-compat` | Avro schema compatibility & evolution checks | `mvn -pl kafka-messaging-tests -P schema-compat verify` |


## DLQ and error handling (implementation notes)
- Consumer uses Spring Kafka's `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`.
- On repeated failure the record is published to a dedicated DLQ topic such as `orders.created.DLQ`.
- The DLQ record preserves the original key and adds headers like `kafka_dlt-exception-message` for diagnostics.


## Avro schemas & evolution
- Schemas live under `src/test/avro/v1` and `src/test/avro/v2`.
- V2 is an additive, optional change (e.g. `promotionCode` optional field) designed to be compatible.
- `SchemaCompatibilityIT` registers schemas and programmatically asserts compatibility under different compatibility modes.
- `SchemaEvolutionIT` demonstrates reader/writer scenarios (V2 writer → V1 reader and V1 writer → V2 reader).


## CI (GitHub Actions)
- `.github/workflows/kafka-tests.yml` orchestrates test jobs and artifacts.
- `kafka-it` and `e2e` jobs upload JUnit XML and Allure results for later inspection.
- `schema-compat` job can be conditioned to run only when `*.avsc` files change (helper script available in `scripts/avro-changed.sh`).


## Troubleshooting
- Docker isn’t running: Testcontainers cannot start containers — start Docker Desktop.
- Corporate VPN / proxy: container networking or image download may fail — try off VPN or configure Docker proxy.
- ARM (Apple Silicon): some Confluent/third-party images may require `platform: linux/amd64` or alternate images. Tests try to use widely compatible images, but your Docker config may need adjusting.
- Schema Registry failures: ensure the configured schema subject names match what tests register.


## Customization
- Rename package `org.example` to your organization if needed.
- Update topic names, schema subjects and DLQ topic in `application-test.yml` and `AvroKafkaTestConfig`.
- Tune retry / backoff behavior and DLQ policy in `AvroKafkaTestConfig`.


## Contributing
- Tests and schema changes should be covered by corresponding test cases.
- When changing Avro schemas, update `src/test/avro` and add/adjust compatibility tests as needed.
- Follow the existing module structure for any new tests or demos.


## License & data
- Test data is synthetic and ephemeral; containers are destroyed after runs.
- No PII included by default.


Contact
- For questions about test design or CI wiring, open an issue or contact the repository maintainers.


Author: eyupUK

Date: 2024-06-10
