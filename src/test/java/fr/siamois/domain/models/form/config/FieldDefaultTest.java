package fr.siamois.domain.models.form.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldDefaultTest {

    @Test
    void roundTripsThroughItsStoredJson() {
        FieldDefault value = FieldDefault.ofConceptIds(List.of("12", "34"));

        String json = value.toJson();

        assertThat(json).isEqualTo("{\"concepts\":[{\"conceptId\":\"12\"},{\"conceptId\":\"34\"}]}");
        assertThat(FieldDefault.fromJson(json)).isEqualTo(value);
        assertThat(FieldDefault.fromJson(json).conceptIds()).containsExactly("12", "34");
    }

    @Test
    void dropsDuplicatesAndBlankIds() {
        FieldDefault value = FieldDefault.ofConceptIds(java.util.Arrays.asList("12", "12", " ", null, "34"));

        assertThat(value.conceptIds()).containsExactly("12", "34");
    }

    @Test
    void anEmptyDefaultIsStoredAsNothing() {
        assertThat(FieldDefault.ofConceptIds(List.of()).toJson()).isNull();
        assertThat(FieldDefault.fromJson(null)).isNull();
        assertThat(FieldDefault.fromJson("  ")).isNull();
        assertThat(FieldDefault.fromJson("{\"concepts\":[]}")).isNull();
    }

    @Test
    void anUnreadableDefaultIsNoDefault() {
        assertThat(FieldDefault.fromJson("not json")).isNull();
    }
}
