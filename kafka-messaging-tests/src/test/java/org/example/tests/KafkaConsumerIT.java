package org.example.tests;

import io.qameta.allure.AllureId;
import org.example.avro.v2.OrderCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaConsumerIT extends KafkaConfigTest {
  @Autowired KafkaTemplate<String, OrderCreated> avroProducer;
  @Autowired JdbcTemplate jdbc;

  // Ensure table exists and is clean before each test run
  @BeforeEach
  void initDb() {
    jdbc.execute("CREATE TABLE IF NOT EXISTS orders(order_id text primary key, total numeric)");
    jdbc.update("TRUNCATE TABLE orders");
  }

  private static ByteBuffer dec(BigDecimal v) { return ByteBuffer.wrap(v.movePointRight(2).unscaledValue().toByteArray()); }

  @Test @AllureId("IT-001")
  void idempotency_noDuplicates() {
    var evt = OrderCreated.newBuilder()
        .setEventId("EVT-1").setOrderId("ORD-999").setCustomerId("C-1")
        .setTotal(dec(new BigDecimal("10.00"))).setTs(Instant.now()).build();

    avroProducer.send("orders.created", "C-1", evt).join();
    avroProducer.send("orders.created", "C-1", evt).join();

    org.awaitility.Awaitility.await().untilAsserted(() -> {
      int cnt;
      try {
        cnt = jdbc.queryForObject("select count(*) from orders where order_id='ORD-999'", Integer.class);
      } catch (org.springframework.jdbc.BadSqlGrammarException e) {
        // Table may not be created yet by the consumer; keep waiting
        cnt = 0;
      }
      assertThat(cnt).isEqualTo(1);
    });
  }
}
