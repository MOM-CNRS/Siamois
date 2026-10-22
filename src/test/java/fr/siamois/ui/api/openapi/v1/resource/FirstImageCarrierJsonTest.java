package fr.siamois.ui.api.openapi.v1.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.ui.api.openapi.v1.resource.find.FindResource;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The property's contract is declared once, on {@link FirstImageCarrier}: both resources follow it. */
class FirstImageCarrierJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(Object resource) {
        return mapper.valueToTree(resource);
    }

    @Test
    void aRecordingUnitWithAnImage_carriesItsId() {
        RecordingUnitResource resource = new RecordingUnitResource();
        resource.setFirstImageDocumentId("42");

        assertEquals("42", json(resource).get("firstImageDocumentId").asText());
    }

    @Test
    void aFindWithAnImage_carriesItsId() {
        FindResource resource = new FindResource();
        resource.setFirstImageDocumentId("7");

        assertEquals("7", json(resource).get("firstImageDocumentId").asText());
    }

    @Test
    void withoutAnImage_thePropertyIsLeftOut() {
        assertFalse(json(new RecordingUnitResource()).has("firstImageDocumentId"));
        assertFalse(json(new FindResource()).has("firstImageDocumentId"));
    }
}
