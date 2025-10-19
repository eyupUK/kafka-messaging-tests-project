package org.example.tests;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class KafkaConfigTest {

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @BeforeAll
    static void start() {
        kafka.start();
        pg.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Use in-memory Mock Schema Registry in tests to avoid external dependency
        r.add("schema.registry.url", () -> "mock://kafka-tests");
        r.add("spring.kafka.properties.schema.registry.url", () -> "mock://kafka-tests");

        // Align topic names with tests
        r.add("app.topics.orders", () -> "orders.created");
        r.add("app.topics.dlq", () -> "orders.created.DLQ");

        // Wire up PostgreSQL container to Spring Datasource auto-config
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

}
