package org.example.tests;

import io.qameta.allure.AllureId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiToKafkaE2E extends KafkaConfigTest {
  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;

  @Test @AllureId("E2E-001")
  void postOrder_goesThroughKafka_andPersists() {
    var body = java.util.Map.of("orderId","ORD-123","customerId","C-42","total", 99.50);
    var resp = rest.postForEntity("/api/orders", body, Void.class);
    assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

    org.awaitility.Awaitility.await().untilAsserted(() -> {
      Integer cnt = jdbc.queryForObject("select count(*) from orders where order_id='ORD-123'", Integer.class);
      assertThat(cnt).isEqualTo(1);
    });
  }
}
