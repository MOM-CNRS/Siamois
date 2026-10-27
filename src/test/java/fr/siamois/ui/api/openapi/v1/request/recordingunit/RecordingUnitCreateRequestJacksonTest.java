package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitCreateRequestJacksonTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void deserialize_legacyFieldAnswers_succeeds() throws Exception {
        String json = """
                {
                  "projectId": "10",
                  "typeId": "42",
                  "fieldAnswers": { "7": "US-01", "8": 3 }
                }
                """;

        assertThat(mapper.canDeserialize(mapper.constructType(RecordingUnitCreateRequest.class))).isTrue();

        RecordingUnitCreateRequest request = mapper.readValue(json, RecordingUnitCreateRequest.class);

        assertThat(request.getProjectId()).isEqualTo("10");
        assertThat(request.getTypeId()).isEqualTo("42");
        assertThat(request.getFieldAnswers()).containsEntry("7", "US-01");
        assertThat(request.getFieldAnswers()).containsEntry("8", 3);
    }

    @Test
    void deserialize_answersEnvelope_andLegacyMerge() throws Exception {
        String json = """
                {
                  "projectId": "10",
                  "recordingUnitTypeConceptId": "42",
                  "answers": { "1": { "value": "A" } },
                  "fieldAnswers": { "2": "B" }
                }
                """;

        RecordingUnitCreateRequest request = mapper.readValue(json, RecordingUnitCreateRequest.class);

        assertThat(request.getTypeId()).isEqualTo("42");
        assertThat(request.getFieldAnswers()).containsEntry("1", "A");
        assertThat(request.getFieldAnswers()).containsEntry("2", "B");
    }

    @Test
    void deserialize_identifier_readsTheCustomIdentifier() throws Exception {
        String json = """
                {
                  "projectId": "10",
                  "typeId": "42",
                  "identifier": "INST-PROJ-UE42"
                }
                """;

        RecordingUnitCreateRequest request = mapper.readValue(json, RecordingUnitCreateRequest.class);

        assertThat(request.getProjectId()).isEqualTo("10");
        assertThat(request.getTypeId()).isEqualTo("42");
        assertThat(request.getIdentifier()).isEqualTo("INST-PROJ-UE42");
    }

    @Test
    void deserialize_withoutIdentifier_leavesItNull_soTheServerGeneratesIt() throws Exception {
        RecordingUnitCreateRequest request = mapper.readValue(
                "{\"projectId\": \"10\", \"typeId\": \"42\"}", RecordingUnitCreateRequest.class);

        assertThat(request.getIdentifier()).isNull();
    }
}
