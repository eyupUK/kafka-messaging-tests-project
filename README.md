# Kafka Messaging Tests (Avro + Schema Registry + DLQ)

A ready-to-run Maven **test module** that spins up **Kafka**, **Schema Registry**, and **PostgreSQL** using **Testcontainers** and verifies a simple **API Gateway → Kafka → Microservice → DB** flow.

**Includes**
- Integration tests (producer & consumer paths, idempotency)
- E2E test (REST → Kafka → DB round trip)
- DLQ assertion (invalid message → Dead Letter Queue with error headers)
- Avro + Confluent Schema Registry wiring
- Schema compatibility checks (BACKWARD & FULL)
- Schema evolution contract tests (V1 ↔ V2 reader/writer resolution)
- GitHub Actions workflow (suites + Allure; conditional schema checks)
- Helper script to run schema checks only when `*.avsc` changed

---

## Prerequisites
- Java **21**
- Maven **3.9+**
- Docker (for Testcontainers)

> No local Kafka or Postgres needed—Testcontainers handles everything.

---

## Quickstart

From the repo root (where the parent `pom.xml` lives):

```bash
# Integration tests (Kafka + DB)
mvn -pl kafka-messaging-tests -P kafka-it verify

# End-to-end test (REST -> Kafka -> DB)
mvn -pl kafka-messaging-tests -P e2e verify

# Avro schema compatibility & evolution tests only
mvn -pl kafka-messaging-tests -P schema-compat verify
```

Allure results (if you want to view locally):

```
kafka-messaging-tests/target/allure-results

# If Allure CLI is installed:
allure serve kafka-messaging-tests/target/allure-results
```

```aiignore
.
├─ pom.xml                              # Parent (aggregator)
├─ kafka-messaging-tests/
│  ├─ pom.xml
│  ├─ src/test/java/org/example/tests/
│  │  ├─ TestApp.java                   # Test-scope Spring Boot: REST -> Kafka; Consumer -> DB
│  │  ├─ AvroKafkaTestConfig.java       # Topics, Avro SerDes, DLQ handler, factories
│  │  ├─ KafkaConfigTest.java           # Testcontainers (Kafka, Schema Registry, Postgres)
│  │  ├─ ApiToKafkaE2E.java             # E2E (REST -> Kafka -> DB)
│  │  ├─ KafkaConsumerIT.java           # Idempotency test
│  │  ├─ DlqAssertionIT.java            # Negative total -> DLQ + header assertions
│  │  ├─ SchemaCompatibilityIT.java     # Registry compatibility: BACKWARD/FULL
│  │  └─ SchemaEvolutionIT.java         # Reader/Writer evolution: V1 <-> V2
│  ├─ src/test/resources/application-test.yml
│  └─ src/test/avro/
│     ├─ v1/OrderCreated.avsc           # Baseline schema
│     └─ v2/OrderCreated.avsc           # V2 (adds optional promotionCode)
├─ scripts/
│  └─ avro-changed.sh                   # Detects *.avsc changes in PRs
└─ .github/workflows/
   └─ kafka-tests.yml                   # CI: kafka-it, e2e, conditional schema-compat, Allure
```

| Profile         | What runs                            | Command                                                 |
| --------------- | ------------------------------------ | ------------------------------------------------------- |
| `kafka-it`      | Integration tests (`*IT.java`)       | `mvn -pl kafka-messaging-tests -P kafka-it verify`      |
| `e2e`           | End-to-end tests (`*E2E.java`)       | `mvn -pl kafka-messaging-tests -P e2e verify`           |
| `schema-compat` | Avro schema compat + evolution tests | `mvn -pl kafka-messaging-tests -P schema-compat verify` |

### Avro & Schema Evolution

Schemas live under src/test/avro/v1 and src/test/avro/v2.

V2 adds an optional promotionCode with default null (safe additive change).

SchemaCompatibilityIT registers V1, sets SR mode to BACKWARD/FULL, and asserts V2 is compatible (and shows a breaking example fails).

SchemaEvolutionIT proves:

V2 writer → V1 reader (backward)

V1 writer → V2 reader (forward, default promotionCode = null)


### DLQ Behavior

A DefaultErrorHandler + DeadLetterPublishingRecoverer send failures to orders.created.DLQ after bounded retries.

DlqAssertionIT consumes the DLQ with a bytes consumer and asserts:

The original key is preserved

kafka_dlt-exception-message header contains the cause


### GitHub Actions

.github/workflows/kafka-tests.yml:

kafka-it and e2e jobs upload JUnit XML & Allure results.

schema-compat runs only when *.avsc changed (via scripts/avro-changed.sh).

allure-report merges and publishes a browsable Allure artifact.

If you use Xray, add a step to POST JUnit XML with repo secrets.


### Customization

Rename package org.example to your org.

Update topic names in application-test.yml & AvroKafkaTestConfig.

Tweak DLQ retry/backoff in AvroKafkaTestConfig.errorHandler(...).

Change schema subjects in SchemaCompatibilityIT if needed.


### Troubleshooting

Docker not running → containers won’t start.

Corporate VPN/Proxy → may block Testcontainers networking; retry off VPN.

ARM Macs → Confluent images generally support ARM; if not, set Docker to use linux/amd64.

Allure missing → tests still pass; report generation is optional.


### Security & Data

100% synthetic data; no real PII.

Containers are ephemeral; DB is created/teardown per run.