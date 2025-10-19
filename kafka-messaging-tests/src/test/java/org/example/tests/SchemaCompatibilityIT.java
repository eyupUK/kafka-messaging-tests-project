package org.example.tests;

import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SchemaCompatibilityIT {

    private static Schema parse(String path) throws Exception {
        return new Schema.Parser().parse(Files.readString(Path.of(path)));
    }

    @Test
    void v2_is_backward_and_full_compatible_with_v1() throws Exception {
        // V1 = baseline, V2 adds optional field with default null
        Schema v1 = parse("src/test/avro/v1/OrderCreated.avsc");
        Schema v2 = parse("src/test/avro/v2/OrderCreated.avsc");

        // BACKWARD: new reader (V2) must be able to read old writer data (V1)
        var backward = SchemaCompatibility.checkReaderWriterCompatibility(v2, v1);
        assertThat(backward.getType())
                .as("V2 must be BACKWARD compatible with V1")
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);

        // FORWARD: old reader (V1) must be able to read new writer data (V2)
        var forward = SchemaCompatibility.checkReaderWriterCompatibility(v1, v2);
        assertThat(forward.getType())
                .as("V2 must be FORWARD compatible with V1 (thanks to default null)")
                .isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);

        // FULL = backward + forward
        assertThat(backward.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
        assertThat(forward.getType()).isEqualTo(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

    @Test
    void removing_required_field_breaks_forward() throws Exception {
        Schema v1 = parse("src/test/avro/v1/OrderCreated.avsc");

        // Build a "broken" schema by removing the required field `customerId`
        // (Using Jackson for a robust edit; Spring Boot's test deps already bring Jackson)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/test/avro/v1/OrderCreated.avsc")));
        var fields = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("fields");

        // Remove the field named "customerId"
        for (int i = 0; i < fields.size(); i++) {
            if ("customerId".equals(fields.get(i).get("name").asText())) {
                fields.remove(i);
                break;
            }
        }
        Schema broken = new Schema.Parser().parse(mapper.writeValueAsString(root));

        // FORWARD: old reader (v1) reading new writer (broken) -> should be INCOMPATIBLE
        var forward = org.apache.avro.SchemaCompatibility.checkReaderWriterCompatibility(v1 /* old reader */, broken /* new writer */);
        org.assertj.core.api.Assertions.assertThat(forward.getType())
                .as("Removing a required field must break FORWARD compatibility (old reader can't find it)")
                .isEqualTo(org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE);
    }

    @Test
    void removing_required_field_keeps_backward_compatible() throws Exception {
        Schema v1 = parse("src/test/avro/v1/OrderCreated.avsc");

        // Same "broken" schema as above (customerId removed)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/test/avro/v1/OrderCreated.avsc")));
        var fields = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("fields");
        for (int i = 0; i < fields.size(); i++) {
            if ("customerId".equals(fields.get(i).get("name").asText())) {
                fields.remove(i);
                break;
            }
        }
        Schema broken = new Schema.Parser().parse(mapper.writeValueAsString(root));

        // BACKWARD: new reader (broken) reading old writer (v1) -> should be COMPATIBLE (reader ignores extra field)
        var backward = org.apache.avro.SchemaCompatibility.checkReaderWriterCompatibility(broken /* new reader */, v1 /* old writer */);
        org.assertj.core.api.Assertions.assertThat(backward.getType())
                .as("Removing a field is still BACKWARD compatible (new reader ignores old extra field)")
                .isEqualTo(org.apache.avro.SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE);
    }

}
