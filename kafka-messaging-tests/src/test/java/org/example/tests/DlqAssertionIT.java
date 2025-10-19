package org.example.tests;

import io.qameta.allure.AllureId;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class DlqAssertionIT extends KafkaConfigTest {

    @Autowired KafkaTemplate<String, Object> genericAvroKafkaTemplate;
    @Autowired org.springframework.kafka.core.ConsumerFactory<byte[], byte[]> bytesConsumerFactory;

    private static ByteBuffer dec(BigDecimal v) {
        return ByteBuffer.wrap(v.movePointRight(2).unscaledValue().toByteArray());
    }

    @Test @AllureId("IT-003")
    void invalidEvent_isReroutedToDLQ_withReasonHeader() throws Exception {
        // Load V2 schema
        Schema v2 = new Schema.Parser().parse(Files.readString(Path.of("src/test/avro/v2/OrderCreated.avsc")));

        // Build invalid record (negative total) to trigger DLQ
        GenericData.Record bad = new GenericData.Record(v2);
        bad.put("eventId", "EVT-bad");
        bad.put("orderId", "ORD-BAD");
        bad.put("customerId", "C-9");
        bad.put("total", dec(new BigDecimal("-5.00")));
        // ts is a logical timestamp-millis; for GenericRecord provide epoch millis (long), not Instant
        bad.put("ts", Instant.now().toEpochMilli());
        bad.put("promotionCode", null);

        // Send and block
        genericAvroKafkaTemplate.send("orders.created", "C-9", bad).join();

        try (var consumer = bytesConsumerFactory.createConsumer("dlq-group", "dlq-client")) {
            consumer.subscribe(List.of("orders.created.DLQ"));
            ConsumerRecord<byte[], byte[]> rec =
                    KafkaTestUtils.getSingleRecord(consumer, "orders.created.DLQ", java.time.Duration.ofSeconds(20));

            assertThat(new String(rec.key(), UTF_8)).isEqualTo("C-9");

            // Spring adds exception headers for DLQ. The top-level message may be a wrapper; check cause/original too.
            var top = rec.headers().lastHeader("kafka_dlt-exception-message");
            var cause = rec.headers().lastHeader("kafka_dlt-exception-cause-message");
            var orig = rec.headers().lastHeader("kafka_dlt-original-exception-message");

            String topMsg = top == null ? null : new String(top.value(), UTF_8);
            String causeMsg = cause == null ? null : new String(cause.value(), UTF_8);
            String origMsg = orig == null ? null : new String(orig.value(), UTF_8);

            assertThat(topMsg != null || causeMsg != null || origMsg != null).as("DLQ must include exception headers").isTrue();
            assertThat(String.valueOf(topMsg) + String.valueOf(causeMsg) + String.valueOf(origMsg))
                    .contains("negative total");
        }
    }
}
