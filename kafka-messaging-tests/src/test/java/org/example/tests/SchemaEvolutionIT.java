package org.example.tests;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaEvolutionIT {

  private static Schema parse(String path) throws Exception {
    return new Schema.Parser().parse(Files.readString(Path.of(path)));
  }

  @Test
  void writerV2_readerV1_isBackwardCompatible() throws Exception {
    Schema v1 = parse("src/test/avro/v1/OrderCreated.avsc");
    Schema v2 = parse("src/test/avro/v2/OrderCreated.avsc");

    GenericData.Record recV2 = new GenericData.Record(v2);
    recV2.put("eventId","EVT-200");
    recV2.put("orderId","ORD-200");
    recV2.put("customerId","C-200");
    recV2.put("total", ByteBuffer.wrap(new BigDecimal("12.34").movePointRight(2).unscaledValue().toByteArray()));
    recV2.put("ts", Instant.now().toEpochMilli());
    recV2.put("promotionCode", "SAVE10");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
    new GenericDatumWriter<GenericData.Record>(v2).write(recV2, enc);
    enc.flush();
    byte[] bytes = out.toByteArray();

    BinaryDecoder dec = DecoderFactory.get().binaryDecoder(bytes, null);
    GenericDatumReader<GenericData.Record> reader = new GenericDatumReader<>(v2, v1);
    GenericData.Record readAsV1 = reader.read(null, dec);

    assertThat(readAsV1.get("orderId").toString()).isEqualTo("ORD-200");
    assertThat(readAsV1.getSchema().getFields().stream().anyMatch(f -> f.name().equals("promotionCode"))).isFalse();
  }

  @Test
  void writerV1_readerV2_isForwardCompatible_defaultsApplied() throws Exception {
    Schema v1 = parse("src/test/avro/v1/OrderCreated.avsc");
    Schema v2 = parse("src/test/avro/v2/OrderCreated.avsc");

    GenericData.Record recV1 = new GenericData.Record(v1);
    recV1.put("eventId","EVT-201");
    recV1.put("orderId","ORD-201");
    recV1.put("customerId","C-201");
    recV1.put("total", ByteBuffer.wrap(new BigInteger("1234").toByteArray()));
    recV1.put("ts", Instant.now().toEpochMilli());

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder enc = EncoderFactory.get().binaryEncoder(out, null);
    new GenericDatumWriter<GenericData.Record>(v1).write(recV1, enc);
    enc.flush();
    byte[] bytes = out.toByteArray();

    BinaryDecoder dec = DecoderFactory.get().binaryDecoder(bytes, null);
    GenericDatumReader<GenericData.Record> reader = new GenericDatumReader<>(v1, v2);
    GenericData.Record readAsV2 = reader.read(null, dec);

    assertThat(readAsV2.get("orderId").toString()).isEqualTo("ORD-201");
    assertThat(readAsV2.get("promotionCode")).isNull();
  }
}
