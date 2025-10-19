package org.example.tests;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.example.avro.v2.OrderCreated;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@TestConfiguration
@EnableKafka
public class AvroKafkaTestConfig {

    @Value("${app.topics.orders}")
    private String ordersTopic;

    @Value("${schema.registry.url}")
    String schemaRegistryUrl;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    @Value("${app.topics.dlq:orders.DLQ}")
    private String dlqTopic;

    // ---------- Topics ----------
    @Bean NewTopic ordersTopic() { return TopicBuilder.name(ordersTopic).partitions(3).replicas(1).build(); }
    @Bean NewTopic dlqTopic()    { return TopicBuilder.name(dlqTopic).partitions(3).replicas(1).build(); }

    // ---------- Specific Avro producer (String key, OrderCreated value) ----------
    @Bean
    public ProducerFactory<String, OrderCreated> orderCreatedProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderCreated> avroKafkaTemplate(
            ProducerFactory<String, OrderCreated> orderCreatedProducerFactory) {
        return new KafkaTemplate<>(orderCreatedProducerFactory);
    }

    // ---------- Generic Avro producer (String key, Object value) ----------
    @Bean
    public ProducerFactory<String, Object> genericAvroProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> genericAvroKafkaTemplate(
            ProducerFactory<String, Object> genericAvroProducerFactory) {
        return new KafkaTemplate<>(genericAvroProducerFactory);
    }

    // ---------- DLQ producer (String key, Avro value) ----------
    @Bean
    public ProducerFactory<Object, Object> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<Object, Object> dltTemplate(ProducerFactory<Object, Object> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    // ---------- Avro consumer (String key, OrderCreated value) ----------
    @Bean
    public ConsumerFactory<String, OrderCreated> avroConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put("specific.avro.reader", true);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // ---------- Listener container factory using Avro consumer and error handler ----------
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreated> kafkaListenerContainerFactory(
            ConsumerFactory<String, OrderCreated> avroConsumerFactory,
            CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreated> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(avroConsumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    // ---------- DLQ handler (bounded retries → DLQ) ----------
    @Bean
    public DeadLetterPublishingRecoverer dltRecoverer(@Qualifier("dltTemplate") KafkaTemplate<Object, Object> dltTemplate) {
        return new DeadLetterPublishingRecoverer(dltTemplate, (rec, ex) -> new TopicPartition(dlqTopic, rec.partition()));
    }

    @Bean
    public CommonErrorHandler errorHandler(DeadLetterPublishingRecoverer dltRecoverer) {
        var handler = new org.springframework.kafka.listener.DefaultErrorHandler(dltRecoverer, new FixedBackOff(0L, 0L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setCommitRecovered(true);
        return handler;
    }

    // ---------- Bytes consumer (DLQ assertions) ----------
    @Bean
    public ConsumerFactory<byte[], byte[]> bytesConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

}
