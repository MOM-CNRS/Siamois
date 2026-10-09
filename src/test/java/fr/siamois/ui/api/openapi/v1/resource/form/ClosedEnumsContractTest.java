package fr.siamois.ui.api.openapi.v1.resource.form;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** The closed sets the v1 contract publishes must be exactly what the code handles. */
class ClosedEnumsContractTest {

    private static Set<String> allowableValuesOf(Class<? extends Record> type, String component) throws Exception {
        Schema schema = type.getDeclaredField(component).getAnnotation(Schema.class);
        return new TreeSet<>(Arrays.asList(schema.allowableValues()));
    }

    @Test
    void fieldAnswerType_isTheSetOfFieldAnswerSubtypes() throws Exception {
        Set<String> handled = new TreeSet<>();
        for (JsonSubTypes.Type t : FieldAnswer.class.getAnnotation(JsonSubTypes.class).value()) {
            handled.addAll(Arrays.asList(t.names()));
        }

        assertThat(allowableValuesOf(FieldResource.class, "answerType")).isEqualTo(handled);
    }

    @Test
    void resourceRefType_coversEveryTypeWithADetailEndpoint() throws Exception {
        Set<String> published = allowableValuesOf(ResourceRef.class, "resourceType");

        assertThat(published)
                .contains("projects", "places", "finds", "recording-units", "phases",
                        "containers", "documents", "concepts", "persons")
                .doesNotContain("action-units", "spatial-units", "action-codes");
    }
}
