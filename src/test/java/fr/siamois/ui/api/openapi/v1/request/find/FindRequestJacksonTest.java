package fr.siamois.ui.api.openapi.v1.request.find;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindRequestJacksonTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void create_deserialize_legacyFieldAnswers_succeeds() throws Exception {
        String json = """
                {
                  "recordingUnitId": "187",
                  "typeId": "42",
                  "fieldAnswers": { "7": "INV-01", "8": 3 }
                }
                """;

        FindCreateRequest request = mapper.readValue(json, FindCreateRequest.class);

        assertThat(request.getRecordingUnitId()).isEqualTo("187");
        assertThat(request.getTypeId()).isEqualTo("42");
        assertThat(request.getFieldAnswers()).containsEntry("7", "INV-01");
        assertThat(request.getFieldAnswers()).containsEntry("8", 3);
    }

    @Test
    void create_deserialize_specimenTypeConceptId_alias() throws Exception {
        String json = """
                {
                  "recordingUnitId": "187",
                  "specimenTypeConceptId": "42"
                }
                """;

        FindCreateRequest request = mapper.readValue(json, FindCreateRequest.class);

        assertThat(request.getTypeId()).isEqualTo("42");
    }

    @Test
    void patch_deserialize_legacyFieldAnswers_succeeds() throws Exception {
        String json = """
                {
                  "fieldAnswers": { "7": "note", "8": { "value": 12 }, "9": { "values": [1, 2] } }
                }
                """;

        FindPatchRequest request = mapper.readValue(json, FindPatchRequest.class);

        assertThat(request.getFieldAnswers()).containsEntry("7", "note");
        assertThat(request.getFieldAnswers()).containsEntry("8", 12);
        assertThat(request.getFieldAnswers()).containsEntry("9", java.util.List.of(1, 2));
    }
}
