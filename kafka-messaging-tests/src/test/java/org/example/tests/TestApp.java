package org.example.tests;

import org.example.avro.v2.OrderCreated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;

@SpringBootApplication
@Import(AvroKafkaTestConfig.class)
public class TestApp {
  public static void main(String[] args) { SpringApplication.run(TestApp.class, args); }

  @RestController
  @RequestMapping("/api/orders")
  static class OrdersController {
    private final KafkaTemplate<String, OrderCreated> kafka;
    private final String topic;

    OrdersController(KafkaTemplate<String, OrderCreated> kafka,
                     @Value("${app.topics.orders}") String topic) {
      this.kafka = kafka; this.topic = topic;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String,Object> body) {
      String orderId = (String) body.get("orderId");
      String customerId = (String) body.get("customerId");
      if (!StringUtils.hasText(orderId) || !StringUtils.hasText(customerId)) return ResponseEntity.badRequest().build();

      BigDecimal total = new BigDecimal(String.valueOf(body.getOrDefault("total", 0)));
      var decimal = ByteBuffer.wrap(total.movePointRight(2).unscaledValue().toByteArray());
      OrderCreated event = OrderCreated.newBuilder()
          .setEventId("EVT-" + orderId)
          .setOrderId(orderId)
          .setCustomerId(customerId)
          .setTotal(decimal)
          .setTs(Instant.now())
          .build();

      kafka.send(topic, customerId, event);
      return ResponseEntity.accepted().build();
    }
  }

  static class OrderService {
    private final JdbcTemplate jdbc;
    OrderService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @KafkaListener(topics = "${app.topics.orders}", groupId = "order-consumers")
    public void onOrder(OrderCreated evt) {
      if (!StringUtils.hasText(evt.getOrderId())) {
        throw new IllegalArgumentException("validation: missing orderId");
      }
      var bd = new BigDecimal(new java.math.BigInteger(evt.getTotal().array()), 2);
      if (bd.signum() < 0) {
        throw new IllegalArgumentException("validation: negative total");
      }
      jdbc.update("CREATE TABLE IF NOT EXISTS orders(order_id text primary key, total numeric)");
      jdbc.update("INSERT INTO orders(order_id,total) VALUES(?,?) ON CONFLICT (order_id) DO NOTHING",
          evt.getOrderId(), bd);
    }
  }

  @Bean public OrderService orderService(JdbcTemplate jdbc) { return new OrderService(jdbc); }
}
